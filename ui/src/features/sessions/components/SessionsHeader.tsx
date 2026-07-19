// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited

interface SessionsHeaderProps {
  sessionCount: number;
}

export function SessionsHeader({ sessionCount }: SessionsHeaderProps) {
  return (
    <div className="flex flex-col gap-1 px-6 py-4">
      <h1 className="text-headline-lg font-bold">Sessions</h1>
      <p className="text-body-md text-on-surface-variant">
        {sessionCount} session{sessionCount !== 1 ? 's' : ''} available
      </p>
    </div>
  );
}
