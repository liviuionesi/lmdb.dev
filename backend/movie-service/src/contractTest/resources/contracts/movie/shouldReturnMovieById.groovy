package contracts.movie

import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description "Should return movie details for ID 550"
    request {
        method GET()
        url "/api/v1/movies/550"
    }

    response {
        status OK()
        headers {
            contentType applicationJson()
        }
        body([
            id: 550,
            title: "Fight Club",
            overview: "A ticking-time-bomb insomniac and a slippery soap salesman channel primal male aggression into a shocking new form of therapy.",
            posterPath: "/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg",
            backdropPath: "/hZkgoQY85KGDiDSpMwWrhFSMi1r.jpg",
            releaseDate: "1999-10-15",
            voteAverage: 8.43,
            voteCount: 26280
        ])
    }
}
