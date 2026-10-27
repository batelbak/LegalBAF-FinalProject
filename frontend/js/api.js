// כל התקשורת עם הבאקנד עוברת מכאן. שאר הקוד לא קורא ל-fetch ישירות.

export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }

  /** השרת קיים אבל החלק הזה במערכת עוד לא מומש. */
  get notImplemented() {
    return this.status === 501;
  }

  get offline() {
    return this.status === 0;
  }
}

async function request(method, path, body) {
  let response;
  try {
    response = await fetch('/api' + path, {
      method,
      headers: body ? { 'Content-Type': 'application/json' } : {},
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(0, 'The server is not reachable.');
  }
  const data = await response.json().catch(() => null);
  if (!response.ok) {
    throw new ApiError(response.status, data?.message || response.statusText);
  }
  return data;
}

const id = caseId => encodeURIComponent(caseId);

export const api = {
  health: () => request('GET', '/health'),
  cases: () => request('GET', '/cases'),
  caseDetails: caseId => request('GET', `/cases/${id(caseId)}`),
  /** מצב א': הטענות האמיתיות מפסק הדין והשקילה של השופט, כרצף אירועים. */
  hearing: caseId => request('GET', `/cases/${id(caseId)}/hearing`),
  tippingPoints: caseId => request('GET', `/cases/${id(caseId)}/tipping-points`),
  startSimulation: (caseId, rounds) => request('POST', '/simulations', { caseId, rounds }),
  counterArguments: (caseId, side, text) => request('POST', '/strategy', { caseId, side, text }),
};
