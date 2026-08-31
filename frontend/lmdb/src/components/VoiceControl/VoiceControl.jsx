import React, { useContext, useRef, useState } from 'react';
import { Fab, CircularProgress, Snackbar, Alert, Tooltip, ToggleButtonGroup, ToggleButton } from '@mui/material';
import { Mic, Stop } from '@mui/icons-material';
import { useDispatch } from 'react-redux';
import { useNavigate } from 'react-router-dom';

import { ColorModeContext } from '../../utils/ToggleColorMode';
import { selectGenreOrCategory, aiSearchStarted, aiSearchSucceeded, aiSearchFailed } from '../../features/currentGenreOrCategory';
import { clearUser } from '../../features/auth';
import { resolveApiUrl } from '../../utils/apiUrl';
import { clearAuthTokens } from '../../utils';
import { encodeToWav } from '../../utils/wavEncoder';
import { parseVoiceCommand } from '../../utils/voiceCommands';
import { getDictationLanguage, setDictationLanguage } from '../../utils/dictationLanguage';
import { useGetGenresQuery } from '../../services/TMDB';
import { useExecuteSearchMutation } from '../../services/AI';
import { toTmdbMovieShape } from '../Search/Search';

/**
 * Click-to-talk voice control (#68): records a
 * short clip via the browser's own mic APIs, sends it to ai-service's
 * self-hosted speech-to-text endpoint, and runs voice commands
 * (browse a genre/category, toggle dark/light mode, log out, search)
 * against the transcribed text.
 *
 * <p>Also owns the EN/DE dictation-language switch (#213, part of #200's
 * bilingual voice control Story): a small persisted toggle next to the mic
 * Fab that selects which Vosk model ai-service transcribes against (#212).
 */
function VoiceControl() {
  const { setMode } = useContext(ColorModeContext);
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const { data: genresData } = useGetGenresQuery();
  const [executeSearch] = useExecuteSearchMutation();

  const [status, setStatus] = useState('idle'); // idle | recording | transcribing
  const [feedback, setFeedback] = useState(null);
  const [language, setLanguage] = useState(getDictationLanguage);
  const mediaRecorderRef = useRef(null);
  const chunksRef = useRef([]);
  // Read via a ref rather than the `language` state directly so that a
  // switch made *after* recording has started (#213: "doesn't ... break an
  // in-progress recording") is still picked up - `transcribeAndRun` fires
  // from the MediaRecorder's onstop handler, bound back in startRecording,
  // so closing over `language` there would freeze it at recording-start time.
  const languageRef = useRef(language);
  languageRef.current = language;

  const handleLanguageChange = (event, newLanguage) => {
    // MUI's exclusive ToggleButtonGroup passes null when the already-selected
    // button is clicked again - ignore that rather than clearing the selection.
    if (!newLanguage) return;
    setLanguage(newLanguage);
    setDictationLanguage(newLanguage);
  };

  const runCommand = async ({ command, mode, genreOrCategory, query }) => {
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
      dispatch(aiSearchStarted(query));
      try {
        const response = await executeSearch(query).unwrap();
        dispatch(aiSearchSucceeded({ results: (response.results ?? []).map(toTmdbMovieShape) }));
      } catch (error) {
        dispatch(aiSearchFailed());
      }
    }
  };

  const transcribeAndRun = async (audioBlob) => {
    setStatus('transcribing');
    try {
      const wavBlob = await encodeToWav(audioBlob);
      const formData = new FormData();
      formData.append('audio', wavBlob, 'command.wav');
      formData.append('language', languageRef.current);

      // Must await the health-checked resolver here rather than the
      // synchronous getApiUrl() - on a cold cache (e.g. this is the first
      // request of the session) that sync fallback returns the cloud
      // default without waiting to confirm anything is actually up there,
      // silently hitting a dead backend when cloud is down and only the
      // tunnel is reachable.
      const baseUrl = await resolveApiUrl();
      const response = await fetch(`${baseUrl}/api/v1/ai/speech-to-text`, {
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
        await runCommand(parsedCommand);
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
      <Tooltip title="Dictation language">
        <ToggleButtonGroup
          value={language}
          exclusive
          size="small"
          onChange={handleLanguageChange}
          aria-label="Dictation language"
          sx={{
            position: 'fixed', right: 20, bottom: 100, zIndex: 1201, backgroundColor: 'background.paper',
          }}
        >
          <ToggleButton value="en" aria-label="English">EN</ToggleButton>
          <ToggleButton value="de" aria-label="German">DE</ToggleButton>
        </ToggleButtonGroup>
      </Tooltip>
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
