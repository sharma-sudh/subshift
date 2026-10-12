import { Component } from 'react';

/** Last line of defence: a rendering bug shows a message instead of a blank white page. */
export default class ErrorBoundary extends Component {
  state = { error: null };

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error, info) {
    console.error('Unhandled UI error:', error, info.componentStack);
  }

  render() {
    if (!this.state.error) return this.props.children;
    return (
      <div className="page narrow">
        <div className="panel empty" role="alert">
          <h1>Something went wrong</h1>
          <p>The page hit an unexpected error. Reloading usually fixes it.</p>
          <button type="button" className="btn btn-primary" onClick={() => window.location.assign('/dashboard')}>
            Back to dashboard
          </button>
        </div>
      </div>
    );
  }
}
