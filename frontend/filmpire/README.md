# Filmpire

A modern movie application built with React that allows users to browse, search, and discover movies.

## Tech Stack

- **Frontend Framework**: React 17.0.2
- **UI Components**: Material-UI (MUI)
- **State Management**: Redux Toolkit
- **Routing**: React Router DOM
- **API Calls**: Axios
- **Voice Integration**: Vosk Speech-to-Text (via `ai-service`)
- **Testing**: Jest and React Testing Library
- **Code Quality**: ESLint with Airbnb config

## Voice Assistant Setup (Vosk Speech-to-Text)

To enable click-to-talk voice commands in the application:

1. **Start the Microservices Backend**:
   Ensure `ai-service` is running on port 8084 (or via API Gateway on port 8080).
   Run `infrastructure/scripts/download-vosk-model.sh` to download the offline English Vosk model (`~40MB`).

2. **Voice Commands**:
   Click the microphone button to record a voice clip. Spoken audio is transcribed locally by `ai-service` and executed:
   - Browse genres (e.g., *"show me action movies"*)
   - Toggle theme (e.g., *"change to dark mode"*)
   - Search movies (e.g., *"search Inception"*)

## Project Structure

- `src/` - Main source code directory
- `public/` - Static assets
- `.env` - Environment variables configuration
- `.github/` - GitHub workflow configurations
- `.vscode/` - VS Code specific settings

## Available Scripts

In the project directory, you can run:

### `npm run dev`

Runs the app in development mode via Vite.\
Open [http://localhost:3000](http://localhost:3000) to view it in your browser.

The page reloads (via HMR) when you make changes.\
You may also see any lint errors in the console.

### `npm test`

Launches the test runner in the interactive watch mode.\

### `npm run build`

Builds the app for production to the `dist` folder.\
It correctly bundles React in production mode and optimizes the build for the best performance.

The build is minified and the filenames include the hashes.\
Your app is ready to be deployed!

## Deployment

The application is deployed on Vercel and can be accessed at [https://filmpire-ten.vercel.app/](https://filmpire-ten.vercel.app/)

## Learn More

The filmpire repository can be found here [Filmpire Repository](https://github.com/pehlivanu/filmpire.git).
