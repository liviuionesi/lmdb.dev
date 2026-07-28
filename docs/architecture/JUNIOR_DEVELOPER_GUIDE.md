# Filmpire: The Complete Junior Developer Guide (Deep Dive Tech Lead Edition)

Welcome to the Filmpire project! If you're a junior developer familiar with basic Java, SQL, HTML, CSS, and JavaScript, you might look at this project and feel a bit overwhelmed by the sheer number of technologies used. Don't worry! This guide—written from the perspective of a Tech Lead—is designed to take you on a deep dive through our entire `ARCHITECTURE.md`. We will demystify all the concepts, buzzwords, and structural decisions so you can confidently contribute to the codebase.

We are building an **enterprise-grade backend clone of the TMDB (The Movie Database) API v3**. The primary goal is that the existing Filmpire React application can consume our backend as a **drop-in replacement** for the real TMDB API by simply changing its base URL. 

---

## 1. The Big Picture: Architecture Buzzwords

### Monolith vs. Microservices
In the past, you would build an entire application (user login, movie search, AI recommendations) into one giant codebase running on one server (a **Monolith**). 
We use a **Microservices Architecture**, splitting the application into multiple small, independent applications (services) communicating over the network.
- **Why?** **Scalability** (scale only the parts that need it) and **Fault Tolerance** (if the AI service dies, users can still log in and view movies).

### The Read-Through Cache Pattern
When our frontend requests movie data, we don't always want to hit the real TMDB API (because of rate limits). Instead, we use a **read-through cache**:
1. Check **Redis** (super fast in-memory cache). If it's there, return it.
2. If not, check **MongoDB** (our persistent database). If it's there, return it and save it to Redis.
3. If not, fetch it from the **real TMDB API**, save it to MongoDB, save it to Redis, and finally return it to the user.
Our local database grows organically as users explore the app!

### REST, gRPC, and Event-Driven Architecture
- **REST APIs**: How the frontend talks to our services via the API Gateway using HTTP and JSON.
- **gRPC**: A super-fast, binary communication protocol developed by Google. We use gRPC for internal, backend-to-backend communication (e.g., between the AI service and other services).
- **Event-Driven (Kafka)**: We use Apache Kafka to broadcast "events". When a movie is fetched and saved, we fire an event (`tmdb.document.saved`) to a message broker. An analytics service can listen to this event in the background to calculate "most-requested movies" without slowing down the user's initial request.

---

## 2. Core Backend Technologies & Development Standards

### Java 25 & Virtual Threads (Project Loom)
Modern Java is incredibly fast and lightweight. We use **Java 25**, leveraging **Virtual Threads**. Handling thousands of concurrent users used to require heavy OS threads. Virtual threads are "cheap" lightweight threads that allow us to handle massive scale with minimal RAM and CPU.
*(Note: We enforce using **Constructor Injection** instead of `@Autowired` fields, and we strictly use **Java Records** for immutable Data Transfer Objects (DTOs).)*

### Spring Boot 4.1.x
Writing a web server from scratch requires thousands of lines of boilerplate. **Spring Boot** is an "opinionated" framework that automatically configures everything for you. You just write your business logic. 

### JPA & Hibernate (Object-Relational Mapping)
You know SQL. But writing raw SQL strings in Java is messy. 
- **JPA (Java Persistence API)** is the standard for mapping Java objects to database tables. 
- **Hibernate** is the tool (ORM) that implements this. You create a Java `User` class, and Hibernate automatically generates the SQL to save and retrieve it from PostgreSQL.

### JWT (JSON Web Tokens)
When a user logs in, how do we remember who they are? Instead of saving sessions in a database, we use **JWT**. The server gives the client a cryptographically signed token. The client sends this token with every HTTP request. It's **stateless**, making it perfect for microservices.

### Gradle
**Gradle** is our build tool. It downloads your dependencies (libraries), compiles the Java code, runs your tests, and packages your app into a runnable `.jar` file. We manage all versions centrally in `gradle.properties`.

---

## 3. Spring Cloud: The Microservices Glue

When you have 8 different services, they need a way to talk to each other and be managed.

- **Discovery Service (Eureka)**: Imagine this as the "Phonebook". When the `movie-service` starts, it registers itself here (e.g., "I'm at IP 192.168.1.5:8081"). If the Gateway wants to route a request, it asks Eureka for the service's IP address.
- **API Gateway**: The **Front Door**. The frontend (React app) sends *all* requests to Port 8080. The Gateway handles global **rate-limiting** (using a tool called `Bucket4j`), **JWT validation**, and routes the request to the correct service.
- **Config Server**: Instead of each service keeping its own config (like passwords or API keys) scattered across the codebase, they ask the central Config Server (which pulls from a Git repo). This allows us to change an API key globally without recompiling code.

---

## 4. The 8 Microservices Explained

1. **API Gateway (8080)**: The front door and load balancer.
2. **Discovery Service (8761)**: The Eureka registry.
3. **Config Service (8888)**: The centralized configuration provider.
4. **Movie Service (8081)**: Uses MongoDB. Handles TMDB catalog data, movie search, and popular movies.
5. **User Service (8082)**: Uses PostgreSQL. Handles JWT authentication, profiles, favorites, and watchlists.
6. **Actor Service (8083)**: Uses PostgreSQL. Maintains structured actor profiles and filmography.
7. **AI Service (8084)**: Uses PostgreSQL with the `pgvector` extension. Interfaces with Spring AI to provide Voice Recognition (Whisper), Movie Recommendations, and Semantic Search using AI embeddings.
8. **Media Service (8085)**: Interfaces with MinIO to store raw files (like user avatars).

---

## 5. The Data Layer: Polyglot Persistence

We use a **Hybrid Database Strategy** (Polyglot Persistence), meaning we pick the right tool for the right job, ensuring a strict "Database-per-Service" rule.

- **PostgreSQL**: A traditional **Relational Database (SQL)**. Used for strictly structured data like User profiles (User Service) and Actor profiles (Actor Service). We also use a special extension called `pgvector` in the AI Service to store machine-learning embeddings for semantic search!
- **MongoDB**: A **NoSQL Database**. Data is stored as JSON-like documents. Movies have flexible, deeply nested data (genres, spoken languages). MongoDB handles this flexible schema easily without requiring complex SQL `JOIN`s.
- **Redis**: An **In-Memory Cache**. Reading from a hard drive is slow. Redis keeps data in RAM. We use it to cache TMDB API responses and manage rate-limits.
- **MinIO**: **Object Storage**. Databases are bad at storing actual files. MinIO is an open-source clone of AWS S3—it stores raw media files efficiently.

---

## 6. Testing, CI/CD, and Observability

### Testing with Testcontainers
We practice Test-Driven Development (TDD). Before we merge code, we run tests using **JUnit 5**. Instead of using a fake, in-memory database like H2, **Testcontainers** automatically spins up a real PostgreSQL or MongoDB Docker container during the test. This guarantees your code works against the real thing. We also use **WireMock** to fake responses from the TMDB API during testing.

### CI/CD (Continuous Integration / Continuous Deployment)
We use **GitHub Actions**. When you push code, a pipeline automatically runs your tests (**CI**). If the tests pass, it builds a Docker image and deploys it (**CD**). No human intervention is needed.

### SonarQube (Static Analysis)
Think of this as a super-advanced spell-checker for code. It scans our Java code for security vulnerabilities, bugs, and "code smells" before we deploy. We enforce an 85% minimum code coverage rule.

### ELK Stack & Distributed Tracing
When code is running across 50 containers, you can't just open a local `.log` file. 
- We use the **ELK Stack** (Elasticsearch, Logstash, Kibana) to centralize all logs. Kibana gives us a beautiful dashboard to search for errors instantly.
- We use **Micrometer Tracing and Zipkin**. When a request hits the Gateway, it gets a unique ID. If that request bounces between the Gateway, User Service, and Movie Service, we can trace exactly how long each hop took and find bottlenecks.

---

## 7. Deployment & Infrastructure as Code (IaC)

### Docker
**Docker** packages your Java app along with the exact Java runtime and OS environment it needs into a single package called a **Container**. It runs exactly the same on your laptop as it does in the cloud. Locally, we use `docker-compose up -d` to spin up PostgreSQL, MongoDB, Redis, and MinIO instantly.

### Kubernetes (K8s)
In production, we need a system to manage containers, restart them if they crash, and scale them up. That system is **Kubernetes**.
- **Pod**: A running instance of your container.
- **Deployment**: The blueprint (e.g., "Keep 3 Movie Service pods running").
- **Service**: The internal load balancer connecting pods.

### Terraform (Infrastructure as Code)
How do we get a Kubernetes cluster in the first place? We use **Terraform**. We write code that defines our infrastructure. Running `terraform apply` automatically talks to AWS/GCP/Azure APIs to spin up our servers and Kubernetes clusters, allowing us to deploy to free-tier cloud environments automatically.

---

## Summary of Your Workflow

1. **Code**: You write a new feature using **Java 25**, **Spring Boot 4.1**, and **JPA/Hibernate**.
2. **Test**: You run `./gradlew test`. **Testcontainers** spins up a real DB to verify your code.
3. **Analyze**: You push to GitHub. **CI/CD** runs tests and **SonarQube** checks for bugs.
4. **Build**: A **Docker** image is built containing your new code.
5. **Provision**: **Terraform** ensures the cloud infrastructure is ready.
6. **Deploy**: **Kubernetes** pulls the new Docker image and spins up new Pods.
7. **Serve**: The **API Gateway** routes traffic from the React frontend to your brand new code.
8. **Monitor**: You watch the logs in **Kibana (ELK)** and track performance in **Zipkin** to make sure everything is running smoothly.

Welcome to modern Enterprise Software Development! Take your time, read the code, and don't hesitate to ask questions. You've got this!
