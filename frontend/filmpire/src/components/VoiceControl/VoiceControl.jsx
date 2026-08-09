import React, { useContext, useRef, useState } from 'react';
import { Fab, CircularProgress, Snackbar, Alert, Tooltip } from '@mui/material';
import { Mic, Stop } from '@mui/icons-material';
import { useDispatch } from 'react-redux';
import { useNavigate } from 'react-router-dom';

import { ColorModeContext } from '../../utils/ToggleColorMode';
import { selectGenreOrCategory, searchMovie } from '../../features/currentGenreOrCategory';
import { clearUser } from '../../features/auth';
import { clearAuthTokens } from '../../utils';
import { encodeToWav } from '../../utils/wavEncoder';
import { parseVoiceCommand } from '../../utils/voiceCommands';
import { useGetGenresQuery } from '../../services/TMDB';

const aiServiceUrl = import.meta.env.VITE_API_URL || 'http://localhost:8080';

/**
 * Click-to-talk voice control (#68): records a
 * short clip via the browser's own mic APIs, sends it to ai-service's
 * self-hosted speech-to-text endpoint, and runs voice commands
 * (browse a genre/category, toggle dark/light mode, log out, search)
 * against the transcribed text.
 */
function VoiceControl() {
  const { setMode } = useContext(ColorModeContext);
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const { data: genresData } = useGetGenresQuery();

  const [status, setStatus] = useState('idle'); // idle | recording | transcribing
  const [feedback, setFeedback] = useState(null);
  const mediaRecorderRef = useRef(null);
  const chunksRef = useRef([]);

  const runCommand = ({ command, mode, genreOrCategory, query }) => {
    const genres = genresData?.genres ?? [];

    if (command === 'chooseGenre') {
      const foundGenre = genres.find((g) => g.name.toLowerCase() === genreOrCategory.toLowerCase());
      navigate('/');
      dispatch(selectGenreOrCategory(foundGenre ? foundGenre.id : genreOrCategory));
    } else if (command === 'changeMode') {
      setMode(mode);
    } else if (command === 'logout') {
      clearAuthTokens();
      dispatch(clearUser());
      navigate('/');
    } else if (command === 'search') {
      navigate('/');
      dispatch(searchMovie(query));
    }
  };

  const transcribeAndRun = async (audioBlob) => {
    setStatus('transcribing');
    try {
      const wavBlob = await encodeToWav(audioBlob);
      const formData = new FormData();
      formData.append('audio', wavBlob, 'command.wav');

      const response = await fetch(`${aiServiceUrl}/api/v1/ai/speech-to-text`, {
        method: 'POST',
        body: formData,
      });

      if (!response.ok) {
        throw new Error(`speech-to-text request failed (${response.status})`);
      }

      const { text } = await response.json();
      const parsedCommand = parseVoiceCommand(text, (genresData?.genres ?? []).map((g) => g.name));

      if (!text) {
        setFeedback({ severity: 'warning', message: "Didn't catch that — try again." });
      } else if (parsedCommand) {
        runCommand(parsedCommand);
        setFeedback({ severity: 'success', message: `Heard: "${text}"` });
      } else {
        setFeedback({ severity: 'info', message: `Heard: "${text}" — no matching command.` });
      }
    } catch (error) {
      setFeedback({ severity: 'error', message: 'Voice control is unavailable right now.' });
    } finally {
      setStatus('idle');
    }
  };

  const startRecording = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mediaRecorder = new MediaRecorder(stream);
      chunksRef.current = [];

      mediaRecorder.ondataavailable = (event) => {
        if (event.data.size > 0) {
          chunksRef.current.push(event.data);
        }
      };

      mediaRecorder.onstop = () => {
        stream.getTracks().forEach((track) => track.stop());
        const audioBlob = new Blob(chunksRef.current, { type: mediaRecorder.mimeType });
        transcribeAndRun(audioBlob);
      };

      mediaRecorderRef.current = mediaRecorder;
      mediaRecorder.start();
      setStatus('recording');
    } catch (error) {
      setFeedback({ severity: 'error', message: 'Microphone access was denied.' });
    }
  };

  const stopRecording = () => {
    mediaRecorderRef.current?.stop();
  };

  const handleClick = () => {
    if (status === 'recording') {
      stopRecording();
    } else if (status === 'idle') {
      startRecording();
    }
  };

  return (
    <>
      <Tooltip title={status === 'recording' ? 'Click to stop recording' : 'Voice control (Try: "Search Batman", "Popular", "Action", "Dark mode")'}>
        <span style={{ position: 'fixed', right: 20, bottom: 40, zIndex: 1201 }}>
          <Fab
            color={status === 'recording' ? 'secondary' : 'primary'}
            onClick={handleClick}
            disabled={status === 'transcribing'}
            aria-label={status === 'recording' ? 'Click to stop recording' : 'Voice control'}
          >
            {status === 'transcribing' ? <CircularProgress size={24} color="inherit" /> : null}
            {status === 'recording' && <Stop />}
            {status === 'idle' && <Mic />}
          </Fab>
        </span>
      </Tooltip>
      <Snackbar
        open={!!feedback}
        autoHideDuration={4000}
        onClose={() => setFeedback(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
      >
        {feedback && <Alert severity={feedback.severity} onClose={() => setFeedback(null)}>{feedback.message}</Alert>}
      </Snackbar>
    </>
  );
}

export default VoiceControl;
