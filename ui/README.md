# Yukta UI

The `ui` module provides the interactive web dashboard for Yukta.

## Technology Stack

- **JTE (Java Template Engine)**: For fast, type-safe server-side rendering.
- **Alpine.js**: For lightweight client-side interactivity.
- **Tailwind CSS**: For modern, responsive styling.
- **D3.js & ELK**: For rendering the interactive Directed Acyclic Graph (DAG) visualization.

## Features

- **Live DAG Visualization**: Monitor workflow execution in real-time.
- **Execution History**: Browse and search past executions.
- **Real-time Log Streaming**: View logs as they are generated.
- **Interactive Tooltips**: Detailed information about each node and its current state.

## Design Principles

- **Unified Layout**: Consistent node sizes (140x80px) and styling.
- **Performance**: Minimal JavaScript, leveraging server-side rendering for speed.
- **Accessibility**: Clear status indicators and intuitive navigation.
