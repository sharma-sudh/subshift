import { Link } from 'react-router-dom';

export default function NotFound() {
  return (
    <div className="page narrow">
      <div className="panel empty">
        <h1>Page not found</h1>
        <p>That address doesn’t exist in SubShift.</p>
        <Link to="/dashboard" className="btn btn-primary">Back to dashboard</Link>
      </div>
    </div>
  );
}
