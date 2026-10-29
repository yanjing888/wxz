// Send structured learning evidence, never HTML, screenshots or unbounded action logs.
export function simulationSubmission(state, revision = Date.now()) {
  if (!state.quizDone || !state.quizConfirmed) return null;
  const result = state.challenge?.result;
  return {
    version: 3, revision, startedAt: state.startedAt,
    installed: [...state.installed], guidePassed: state.guidePassed, quizDone: state.quizDone,
    quizConfirmed: true,
    answers: state.answers.map(({ id, selected }) => ({ id, selected: [...selected] })),
    challenge: result ? { optics: { ...result.optics }, durationSeconds: result.durationSeconds, edits: result.edits } : null,
  };
}
