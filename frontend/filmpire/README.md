# Filmpire

A modern movie application built with React that allows users to browse, search, and discover movies.

## Tech Stack

- **Frontend Framework**: React 17.0.2
- **UI Components**: Material-UI (MUI)
- **State Management**: Redux Toolkit
- **Routing**: React Router DOM
- **API Calls**: Axios
- **Voice Integration**: Alan AI SDK
- **Testing**: Jest and React Testing Library
- **Code Quality**: ESLint with Airbnb config

## Alan AI Voice Assistant Setup

To enable voice commands in the application:

1. **Sign up for Alan AI**:
   - Go to [Alan AI's website](https://alan.app/)
   - Create an account and a new project in Alan Studio

2. **Configure Environment Variables**:
   Add your Alan AI API key to the `.env` file:
   ```
   REACT_APP_ALAN_KEY=your_alan_api_key_here
   ```

3. **Voice Commands**:
   The application supports the following voice commands:
   - `chooseGenre`: Select a movie genre
   - `changeMode`: Toggle between light and dark themes
   - `login`: Initiate login process
   - `logout`: Log out the user
   - `search`: Search for movies

4. **Testing Voice Commands**:
   - Run the application
   - The Alan button appears in the bottom-right corner
   - Click it or say "Alan" to activate voice commands
   - Test each command to ensure they work as expected

## Project Structure

- `src/` - Main source code directory
- `public/` - Static assets
- `.env` - Environment variables configuration
- `.github/` - GitHub workflow configurations
- `.vscode/` - VS Code specific settings

## Available Scripts

In the project directory, you can run:

### `npm start`

Runs the app in the development mode.\
Open [http://localhost:3000](http://localhost:3000) to view it in your browser.

The page will reload when you make changes.\
You may also see any lint errors in the console.

### `npm test`

Launches the test runner in the interactive watch mode.\

### `npm run build`

Builds the app for production to the `build` folder.\
It correctly bundles React in production mode and optimizes the build for the best performance.

The build is minified and the filenames include the hashes.\
Your app is ready to be deployed!

## Deployment

The application is deployed on Vercel and can be accessed at [https://filmpire-ten.vercel.app/](https://filmpire-ten.vercel.app/)

## Learn More

The filmpire repository can be found here [Filmpire Repository](https://github.com/pehlivanu/filmpire.git).
