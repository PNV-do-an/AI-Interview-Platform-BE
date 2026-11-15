import React, { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

const LoginPage: React.FC = () => {
  const { login, loading, error } = useAuth();
  const [searchParams] = useSearchParams();
  const [form, setForm] = useState({ email: '', password: '' });
  const [rememberMe, setRememberMe] = useState(false);

  const registered = searchParams.get('registered');

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm({ ...form, [e.target.name]: e.target.value });
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    login(form, rememberMe);
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-primary/5 to-secondary/5 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        {/* Logo & Branding */}
        <div className="text-center mb-8">
          <div className="flex items-center justify-center gap-3 mb-6">
            <div className="w-12 h-12 rounded-lg bg-primary-container flex items-center justify-center">
              <svg className="w-7 h-7 text-on-primary" fill="currentColor" viewBox="0 0 20 20">
                <path d="M13 7H7v6h6V7z" />
                <path fillRule="evenodd" d="M7 2a1 1 0 012 0v1h2V2a1 1 0 112 0v1h2V2a1 1 0 112 0v1h1a2 2 0 012 2v2h1a1 1 0 110 2h-1v2h1a1 1 0 110 2h-1v1a2 2 0 01-2 2h-1v1a1 1 0 11-2 0v-1h-2v1a1 1 0 11-2 0v-1H7a2 2 0 01-2-2v-1H4a1 1 0 110-2h1V9H4a1 1 0 010-2h1V5a2 2 0 012-2h1V2zM9 5H7v2h2V5z" clipRule="evenodd" />
              </svg>
            </div>
            <div className="text-left">
              <h1 className="font-headline-sm text-headline-sm text-on-surface font-bold">TalentAI</h1>
              <p className="font-label-sm text-label-sm text-on-surface-variant">Career Intelligence</p>
            </div>
          </div>
          <h2 className="font-headline-md text-headline-md text-on-surface font-bold mb-2">Welcome Back</h2>
          <p className="font-body-md text-body-md text-on-surface-variant">Sign in to access your CV and interview prep</p>
        </div>

        {/* Card */}
        <div className="bg-surface-container-lowest rounded-2xl shadow-lg p-8 space-y-6">
          {/* Success Message */}
          {registered === 'true' && (
            <div className="rounded-xl bg-secondary-container/20 border border-secondary-container p-4 flex items-start gap-3">
              <svg className="w-5 h-5 text-secondary mt-0.5 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
              </svg>
              <div>
                <p className="font-label-md text-label-md text-secondary font-semibold">Registration successful!</p>
                <p className="font-body-sm text-body-sm text-on-surface-variant mt-1">
                  Please check your email to activate your account.
                </p>
              </div>
            </div>
          )}

          {/* Error Message */}
          {error && (
            <div className="rounded-xl bg-error-container/20 border border-error-container p-4 flex items-start gap-3">
              <svg className="w-5 h-5 text-error mt-0.5 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
              </svg>
              <div>
                <p className="font-label-md text-label-md text-error font-semibold">Login failed</p>
                <p className="font-body-sm text-body-sm text-on-surface-variant mt-1">{error}</p>
              </div>
            </div>
          )}

          {/* Form */}
          <form onSubmit={handleSubmit} className="space-y-5">
            {/* Email Input */}
            <div className="space-y-2">
              <label htmlFor="email" className="font-label-md text-label-md text-on-surface font-medium flex items-center gap-1.5">
                <svg className="w-5 h-5 text-secondary" fill="currentColor" viewBox="0 0 20 20">
                  <path d="M2.003 5.884L10 9.882l7.997-3.998A2 2 0 0016 4H4a2 2 0 00-1.997 1.884z" />
                  <path d="M18 8.118l-8 4-8-4V14a2 2 0 002 2h12a2 2 0 002-2V8.118z" />
                </svg>
                Email
              </label>
              <input
                id="email"
                type="email"
                name="email"
                value={form.email}
                onChange={handleChange}
                required
                placeholder="you@example.com"
                className="w-full h-12 px-4 rounded-lg bg-surface-container-low border border-transparent text-body-md text-on-surface placeholder-on-surface-variant focus:outline-none focus:bg-surface-container focus:ring-2 focus:ring-primary/30 transition-all"
              />
            </div>

            {/* Password Input */}
            <div className="space-y-2">
              <label htmlFor="password" className="font-label-md text-label-md text-on-surface font-medium flex items-center gap-1.5">
                <svg className="w-5 h-5 text-secondary" fill="currentColor" viewBox="0 0 20 20">
                  <path fillRule="evenodd" d="M5 9V7a5 5 0 0110 0v2a2 2 0 012 2v5a2 2 0 01-2 2H5a2 2 0 01-2-2v-5a2 2 0 012-2zm8-2v2H7V7a3 3 0 016 0z" clipRule="evenodd" />
                </svg>
                Password
              </label>
              <input
                id="password"
                type="password"
                name="password"
                value={form.password}
                onChange={handleChange}
                required
                placeholder="••••••••"
                className="w-full h-12 px-4 rounded-lg bg-surface-container-low border border-transparent text-body-md text-on-surface placeholder-on-surface-variant focus:outline-none focus:bg-surface-container focus:ring-2 focus:ring-primary/30 transition-all"
              />
            </div>

            {/* Remember Me */}
            <div className="flex items-center gap-2">
              <input
                type="checkbox"
                id="rememberMe"
                checked={rememberMe}
                onChange={(e) => setRememberMe(e.target.checked)}
                className="w-4 h-4 rounded cursor-pointer"
              />
              <label htmlFor="rememberMe" className="font-label-sm text-label-sm text-on-surface-variant cursor-pointer">
                Remember me for 30 days
              </label>
            </div>

            {/* Submit Button */}
            <button
              type="submit"
              disabled={loading}
              className="w-full h-12 rounded-lg bg-primary text-on-primary font-label-lg text-label-lg font-semibold hover:bg-primary-container disabled:opacity-50 disabled:cursor-not-allowed transition-all flex items-center justify-center gap-2"
            >
              {loading ? (
                <>
                  <svg className="w-5 h-5 animate-spin" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                  </svg>
                  Signing in...
                </>
              ) : (
                <>
                  <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M4 4a2 2 0 00-2 2v4a1 1 0 102 0V6h10V4a2 2 0 00-2-2H4zm2 6a1 1 0 100 2h12a1 1 0 100-2H6zm0 4a1 1 0 100 2h12a1 1 0 100-2H6z" clipRule="evenodd" />
                  </svg>
                  Sign In
                </>
              )}
            </button>
          </form>

          {/* Divider */}
          <div className="relative">
            <div className="absolute inset-0 flex items-center">
              <div className="w-full border-t border-outline-variant"></div>
            </div>
            <div className="relative flex justify-center text-sm">
              <span className="px-3 bg-surface-container-lowest font-body-sm text-body-sm text-on-surface-variant">
                Need help?
              </span>
            </div>
          </div>

          {/* Links */}
          <div className="space-y-3">
            <Link
              to="/forgot-password"
              className="w-full h-11 rounded-lg bg-surface-container hover:bg-surface-container-high text-on-surface font-label-md text-label-md flex items-center justify-center gap-2 transition-colors"
            >
              <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                <path d="M8.5 9a3.5 3.5 0 11-7 0 3.5 3.5 0 017 0z" />
                <path d="M14.5 16a6 6 0 11-12 0 6 6 0 0112 0zM9 13a1 1 0 11-2 0 1 1 0 012 0zM7 12a1 1 0 100-2 1 1 0 000 2zm4-2a1 1 0 11-2 0 1 1 0 012 0z" />
              </svg>
              Forgot Password?
            </Link>
          </div>
        </div>

        {/* Register Link */}
        <div className="text-center mt-6">
          <p className="font-body-md text-body-md text-on-surface-variant">
            Don't have an account?{' '}
            <Link
              to="/register"
              className="text-primary font-semibold hover:text-primary-container transition-colors"
            >
              Create one
            </Link>
          </p>
        </div>

        {/* Footer */}
        <div className="text-center mt-8">
          <p className="font-label-sm text-label-sm text-on-surface-variant">
            TalentAI © 2026 • All rights reserved
          </p>
        </div>
      </div>
    </div>
  );
};

export default LoginPage;
