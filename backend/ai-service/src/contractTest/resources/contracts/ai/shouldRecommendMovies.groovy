package contracts.ai

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Should return movie recommendations for the caller identified by X-User-Id"
    request {
        method POST()
        url "/api/v1/ai/recommendations"
        headers {
            contentType applicationJson()
            // The caller's identity comes from the gateway-issued header, never
            // from the body — see dev.lmdb.ai.security.CallerIdentity.
            header("X-User-Id", "123e4567-e89b-12d3-a456-426614174000")
        }
        body([
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
