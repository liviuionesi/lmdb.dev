# Media Service

File upload and media management service for images and videos.

**Port:** 8085  
**Database:** MongoDB  
**Storage:** MinIO / Local Filesystem

## Responsibilities

- File upload/download
- Image resizing and thumbnail generation
- Video processing
- Media metadata management
- Storage management (MinIO/S3-compatible)

## Technology Stack

- Spring Boot 3.5.8
- Spring Data MongoDB
- MinIO Client
- Eureka Client

## Running Locally

```bash
# Start MongoDB and MinIO
docker-compose up -d mongodb minio

# Run service
./gradlew :backend:media-service:bootRun
```

## Docker

```bash
docker build -t filmpire/media-service:latest .
docker run -p 8085:8085 filmpire/media-service:latest
```

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/media/upload` | POST | Upload media file |
| `/api/v1/media/{id}` | GET | Download media file |
| `/api/v1/media/{id}` | DELETE | Delete media file |
| `/api/v1/media/entity/{entityId}` | GET | Get all media for entity |
| `/api/v1/media/{id}/thumbnail` | GET | Get thumbnail |

## Database Schema

```javascript
// Media collection
{
  _id: ObjectId,
  entityId: String,        // movie ID or actor ID
  entityType: String,      // MOVIE, ACTOR, USER
  mediaType: String,       // POSTER, BACKDROP, PROFILE, VIDEO, THUMBNAIL
  originalFilename: String,
  storagePath: String,
  fileSize: Number,
  mimeType: String,
  thumbnails: {            // size -> URL mapping
    small: String,
    medium: String,
    large: String
  },
  metadata: {
    width: Number,
    height: Number,
    duration: Number,      // for videos
    codec: String,
    bitrate: Number
  },
  uploadedAt: Date,
  uploadedBy: String
}
```

## Supported File Types

### Images
- JPEG/JPG
- PNG
- GIF
- WebP

### Videos
- MP4
- WebM
- MOV

## Thumbnail Sizes

- **Small:** 150x150px
- **Medium:** 500x500px
- **Large:** 1200x1200px

## Storage Configuration

### MinIO (Recommended for Production)
```yaml
minio:
  endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin
  bucket: filmpire-media
```

### Local Filesystem (Development)
```yaml
storage:
  type: filesystem
  base-path: /var/filmpire/media
```

## Testing

```bash
./gradlew :backend:media-service:test
./gradlew :backend:media-service:jacocoTestReport
```

## OpenAPI Documentation

- Swagger UI: http://localhost:8085/swagger-ui.html
- OpenAPI Spec: http://localhost:8085/v3/api-docs

