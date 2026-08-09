package contracts.actor

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Should return actor details for TMDB person ID 819"
    request {
        method GET()
        url "/api/v1/actors/819"
    }

    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body([
            success: true,
            message: "Actor retrieved",
            statusCode: 200,
            data: [
                tmdbId: 819,
                name: "Edward Norton",
                biography: "Edward Harrison Norton is an American actor and filmmaker.",
                birthDate: "1969-08-18",
                birthPlace: "Boston, Massachusetts, USA",
                profilePath: "/5XB9m1Jl51VI9DchxIMjG6EKOxJ.jpg",
                popularity: 28.5,
                alsoKnownAs: ["Edward Harrison Norton"],
                knownForDepartment: "Acting",
                gender: 2,
                imdbId: "nm0001570",
                homepage: null,
                adult: false,
                _links: [
                    self: [href: "http://localhost/api/v1/actors/819"],
                    movies: [href: "http://localhost/api/v1/actors/819/movies?page=1&size=20"],
                    images: [href: "http://localhost/api/v1/actors/819/images"]
                ]
            ]
        ])
    }
}
