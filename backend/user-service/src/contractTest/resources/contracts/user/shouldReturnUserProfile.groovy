package contracts.user

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Should return user profile for authenticated user"
    request {
        method GET()
        url "/api/v1/users/profile"
    }

    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body([
            success: true,
            message: "Profile retrieved",
            statusCode: 200,
            data: [
                id: "123e4567-e89b-12d3-a456-426614174000",
                username: "liviu",
                email: "liviu@example.com",
                role: "ROLE_USER",
                _links: [
                    self: [href: "http://localhost/api/v1/users/profile"],
                    favorites: [href: "http://localhost/api/v1/users/favorites"],
                    watchlist: [href: "http://localhost/api/v1/users/watchlist"]
                ]
            ]
        ])
    }
}
