// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Infenia Private Limited

import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { TaskSequenceList } from '../TaskSequenceList';
import { createMockGraph, createMockProgress } from '@/test/factories/workflowFactory';
import type { TaskProgress } from '../../types/workflow';

function tasksByNode(): Record<string, TaskProgress> {
  return Object.fromEntries(createMockProgress().tasks.map((task) => [task.nodeId, task]));
}

describe('TaskSequenceList', () => {
  it('renders one item per node in topological order', () => {
    render(<TaskSequenceList graph={createMockGraph()} tasksByNode={tasksByNode()} />);

    const items = screen.getAllByTestId(/task-item-/);
    expect(items).toHaveLength(4);
    expect(items[0]).toHaveAttribute('data-testid', 'task-item-data-ingress');
    expect(items[3]).toHaveAttribute('data-testid', 'task-item-sink-storage');
  });

  it('shows status labels, module and timing per task', () => {
    render(<TaskSequenceList graph={createMockGraph()} tasksByNode={tasksByNode()} />);

    const completed = screen.getByTestId('task-item-data-ingress');
    expect(completed).toHaveTextContent('COMPLETED');
    expect(completed).toHaveTextContent('module: ingress-v1');
    expect(completed).toHaveTextContent('Start: 14:22:01');
    expect(completed).toHaveTextContent('End: 14:22:10');

    const running = screen.getByTestId('task-item-vectorize-batch');
    expect(running).toHaveTextContent('RUNNING');
    expect(running).toHaveTextContent('Dur:');
  });

  it('dims pending tasks and omits timing when not started', () => {
    render(<TaskSequenceList graph={createMockGraph()} tasksByNode={tasksByNode()} />);

    const pending = screen.getByTestId('task-item-sink-storage');
    expect(pending).toHaveTextContent('PENDING');
    expect(pending.className).toContain('opacity-60');
    expect(pending).not.toHaveTextContent('Start:');
  });

  it('falls back to all-pending entries for a never-run workflow', () => {
    render(<TaskSequenceList graph={createMockGraph()} tasksByNode={{}} />);

    const items = screen.getAllByTestId(/task-item-/);
    expect(items).toHaveLength(4);
    items.forEach((item) => expect(item).toHaveTextContent('PENDING'));
    expect(screen.getByTestId('task-item-data-ingress')).toHaveTextContent('module: ingress-v1');
  });

  it('falls back to node declaration order when topologicalOrder is empty', () => {
    const graph = createMockGraph({ topologicalOrder: [] });
    render(<TaskSequenceList graph={graph} tasksByNode={{}} />);
    expect(screen.getAllByTestId(/task-item-/)).toHaveLength(4);
  });

  it('labels unknown nodes in the order with an unknown module', () => {
    const graph = createMockGraph({
      topologicalOrder: ['ghost-node', 'data-ingress', 'vectorize-batch', 'anomaly-detection'],
    });
    render(<TaskSequenceList graph={graph} tasksByNode={{}} />);
    expect(screen.getByTestId('task-item-ghost-node')).toHaveTextContent('module: unknown');
  });

  it('marks failed tasks with the FAILED label and error styling', () => {
    const tasks = tasksByNode();
    tasks['vectorize-batch'] = { ...tasks['vectorize-batch'], status: 'FAILURE' };
    render(<TaskSequenceList graph={createMockGraph()} tasksByNode={tasks} />);

    const failed = screen.getByTestId('task-item-vectorize-batch');
    expect(failed).toHaveTextContent('FAILED');
    expect(failed.querySelector('.text-error')).not.toBeNull();
  });
});
