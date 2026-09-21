import { useContext, useRef, useState, useEffect } from 'react';
import { useDispatch } from 'react-redux';
import { useNavigate } from 'react-router-dom';

import { ColorModeContext } from '../../utils/ToggleColorMode';
import { selectGenreOrCategory, dictatedQuerySubmitted } from '../../features/currentGenreOrCategory';
import { clearUser } from '../../features/auth';
import { resolveApiUrl } from '../../utils/apiUrl';
import { clearAuthTokens } from '../../utils';
import { encodeToWav } from '../../utils/wavEncoder';
import { parseVoiceCommand } from '../../utils/voiceCommands';
import { getDictationLanguage, setDictationLanguage as persistDictationLanguage } from '../../utils/dictationLanguage';
import { useGetGenresQuery } from '../../services/TMDB';

export const useVoiceControl = () => {
  const { setMode } = useContext(ColorModeContext);
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const { data: genresData } = useGetGenresQuery();

  const [status, setStatus] = useState('idle'); // idle | recording | transcribing
  const [feedback, setFeedback] = useState(null);
  const [language, setLanguage] = useState(getDictationLanguage);
  const mediaRecorderRef = useRef(null);
  const chunksRef = useRef([]);
  const languageRef = useRef(language);

  useEffect(() => {
    languageRef.current = language;
  }, [language]);

  const setDictationLanguage = (newLanguage) => {
    setLanguage(newLanguage);
    persistDictationLanguage(newLanguage);
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
      dispatch(dictatedQuerySubmitted(query));
    }
  };

  const transcribeAndRun = async (audioBlob) => {
    setStatus('transcribing');
    try {
      const wavBlob = await encodeToWav(audioBlob);
      const formData = new FormData();
      formData.append('audio', wavBlob, 'command.wav');
      formData.append('language', languageRef.current);

      const baseUrl = await resolveApiUrl();
      const response = await fetch(`${baseUrl}/api/v1/ai/speech-to-text`, {
        method: 'POST',
        body: formData,
      });

      if (!response.ok) {
        throw new Error(`speech-to-text request failed (${response.status})`);
      }

      const { text } = await response.json();
      const parsedCommand = await parseVoiceCommand(text, (genresData?.genres ?? []).map((g) => g.name));

      if (!text) {
        setFeedback({ severity: 'warning', message: "Didn't catch that — try again." });
      } else if (parsedCommand) {
        await runCommand(parsedCommand);
        setFeedback({ severity: 'success', message: `Heard: "${text}"` });
      } else {
        setFeedback({ severity: 'info', message: `Heard: "${text}" — no matching command.` });
      }
    } catch (error) {
      console.error('Transcription or command execution failed:', error);
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
      console.error('Failed to start recording:', error);
      setFeedback({ severity: 'error', message: 'Microphone access was denied.' });
    }
  };

  const stopRecording = () => {
    mediaRecorderRef.current?.stop();
  };

  const toggleRecording = () => {
    if (status === 'recording') {
      stopRecording();
    } else if (status === 'idle') {
      startRecording();
    }
  };

  const clearFeedback = () => setFeedback(null);

  return {
    status,
    feedback,
    language,
    setDictationLanguage,
    toggleRecording,
    clearFeedback,
  };
};
