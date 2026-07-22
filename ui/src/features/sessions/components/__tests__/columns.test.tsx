// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited

import { describe, it, expect } from 'vitest';
import { columns } from '../columns';

describe('columns', () => {
  it('should export columns array', () => {
    expect(columns).toBeDefined();
    expect(Array.isArray(columns)).toBe(true);
  });

  it('should have correct number of columns', () => {
    expect(columns).toHaveLength(8);
  });

  it('should have name column', () => {
    const nameColumn = columns.find((col: any) => col.accessorKey === 'name');
    expect(nameColumn).toBeDefined();
    expect(nameColumn?.header).toBe('Name');
  });

  it('should have sessionId column', () => {
    const idColumn = columns.find((col: any) => col.accessorKey === 'sessionId');
    expect(idColumn).toBeDefined();
    expect(idColumn?.header).toBe('Session ID');
  });

  it('should have description column', () => {
    const descColumn = columns.find((col: any) => col.accessorKey === 'description');
    expect(descColumn).toBeDefined();
    expect(descColumn?.header).toBe('Description');
  });

  it('should have initiator column', () => {
    const initiatorColumn = columns.find((col: any) => col.accessorKey === 'initiator');
    expect(initiatorColumn).toBeDefined();
    expect(initiatorColumn?.header).toBe('Initiator');
  });

  it('should have tags column', () => {
    const tagsColumn = columns.find((col: any) => col.accessorKey === 'tags');
    expect(tagsColumn).toBeDefined();
    expect(tagsColumn?.header).toBe('Tags');
  });

  it('should have projectPath column', () => {
    const pathColumn = columns.find((col: any) => col.accessorKey === 'projectPath');
    expect(pathColumn).toBeDefined();
    expect(pathColumn?.header).toBe('Project Path');
  });

  it('should have workflowCount column', () => {
    const countColumn = columns.find((col: any) => col.accessorKey === 'workflowCount');
    expect(countColumn).toBeDefined();
    expect(countColumn?.header).toBe('Workflows');
  });

  it('should have actions column', () => {
    const actionsColumn = columns.find((col: any) => col.id === 'actions');
    expect(actionsColumn).toBeDefined();
    expect(actionsColumn?.id).toBe('actions');
  });

  it('should have cell renderers for custom columns', () => {
    const nameColumn = columns.find((col: any) => col.accessorKey === 'name');
    const tagsColumn = columns.find((col: any) => col.accessorKey === 'tags');
    const actionsColumn = columns.find((col: any) => col.id === 'actions');

    expect(nameColumn?.cell).toBeDefined();
    expect(tagsColumn?.cell).toBeDefined();
    expect(actionsColumn?.cell).toBeDefined();
  });

  it('should include all required session fields as columns', () => {
    const accessorKeys = columns
      .map((col: any) => col.accessorKey)
      .filter(Boolean);

    expect(accessorKeys).toContain('name');
    expect(accessorKeys).toContain('sessionId');
    expect(accessorKeys).toContain('description');
    expect(accessorKeys).toContain('initiator');
    expect(accessorKeys).toContain('tags');
    expect(accessorKeys).toContain('projectPath');
    expect(accessorKeys).toContain('workflowCount');
  });
});
