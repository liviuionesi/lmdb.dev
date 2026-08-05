package contracts.ai

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Should return movie recommendations for user"
    request {
        method POST()
        url "/api/v1/ai/recommendations"
        headers {
            contentType applicationJson()
        }
        body([
            userId: "123e4567-e89b-12d3-a456-426614174000",
            recentMovies: ["Inception", "Interstellar"],
            count: 5
        ])
    }
    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body([
            recommendations: [
                [
                    movieId: "550",
                    score: 0.95,
                    reason: "Based on your preference for psychological thrillers."
                ]
            ]
        ])
    }
}
