import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

interface FormData {
  fullName: string;
  email: string;
  phone: string;
  password: string;
  confirmPassword: string;
}

const RegisterPage: React.FC = () => {
  const { register, loading, error } = useAuth();
  const [form, setForm] = useState<FormData>({
    fullName: '', email: '', phone: '', password: '', confirmPassword: '',
  });
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm({ ...form, [e.target.name]: e.target.value });
    if (fieldErrors[e.target.name]) {
      setFieldErrors({ ...fieldErrors, [e.target.name]: '' });
    }
  };

  const validatePassword = (password: string) => {
    const checks = [
      password.length >= 8,
      /[A-Z]/.test(password),
      /[a-z]/.test(password),
      /\d/.test(password),
      /[@#$%^&+=!]/.test(password),
    ];
    return checks.every(Boolean);
  };

  const getStrengthColor = () => {
    const p = form.password;
    if (!p) return { msg: '', bgClass: '' };
    const score = [p.length >= 8, /[A-Z]/.test(p), /[a-z]/.test(p), /\d/.test(p), /[@#$%^&+=!]/.test(p)].filter(Boolean).length;
    if (score <= 2) return { msg: 'Weak', bgClass: 'bg-error' };
    if (score <= 3) return { msg: 'Fair', bgClass: 'bg-secondary' };
    if (score <= 4) return { msg: 'Good', bgClass: 'bg-primary' };
    return { msg: 'Strong', bgClass: 'bg-secondary-container' };
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const errs: Record<string, string> = {};
    if (!form.fullName.trim()) errs.fullName = 'Full name is required';
    if (!form.email.trim()) errs.email = 'Email is required';
    if (!form.phone.trim()) errs.phone = 'Phone number is required';
    if (!validatePassword(form.password)) errs.password = 'Password must contain: uppercase, lowercase, number & special char';
    if (form.password !== form.confirmPassword) errs.confirmPassword = 'Passwords do not match';
    setFieldErrors(errs);
    if (Object.keys(errs).length > 0) return;
    register({ fullName: form.fullName, email: form.email, password: form.password, phone: form.phone });
  };

  const strengthInfo = getStrengthColor();

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
          <h2 className="font-headline-md text-headline-md text-on-surface font-bold mb-2">Create Account</h2>
          <p className="font-body-md text-body-md text-on-surface-variant">Join TalentAI to boost your career</p>
        </div>

        {/* Card */}
        <div className="bg-surface-container-lowest rounded-2xl shadow-lg p-8 space-y-6">
          {/* Error Message */}
          {error && (
            <div className="rounded-xl bg-error-container/20 border border-error-container p-4 flex items-start gap-3">
              <svg className="w-5 h-5 text-error mt-0.5 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
              </svg>
              <p className="font-body-sm text-body-sm text-on-surface-variant">{error}</p>
            </div>
          )}

          {/* Form */}
          <form onSubmit={handleSubmit} className="space-y-4">
            {/* Full Name */}
            <div className="space-y-1.5">
              <label htmlFor="fullName" className="font-label-md text-label-md text-on-surface font-medium flex items-center gap-1.5">
                <svg className="w-4 h-4 text-secondary" fill="currentColor" viewBox="0 0 20 20">
                  <path d="M10 9a3 3 0 100-6 3 3 0 000 6zm-7 9a7 7 0 1114 0H3z" />
                </svg>
                Full Name
              </label>
              <input
                id="fullName"
                type="text"
                name="fullName"
                value={form.fullName}
                onChange={handleChange}
                required
                placeholder="John Doe"
                className={`w-full h-11 px-4 rounded-lg bg-surface-container-low text-body-md text-on-surface placeholder-on-surface-variant focus:outline-none focus:bg-surface-container focus:ring-2 focus:ring-primary/30 transition-all ${fieldErrors.fullName ? 'border-2 border-error' : 'border border-transparent'}`}
              />
              {fieldErrors.fullName && <p className="font-body-sm text-body-sm text-error">{fieldErrors.fullName}</p>}
            </div>

            {/* Email */}
            <div className="space-y-1.5">
              <label htmlFor="email" className="font-label-md text-label-md text-on-surface font-medium flex items-center gap-1.5">
                <svg className="w-4 h-4 text-secondary" fill="currentColor" viewBox="0 0 20 20">
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
                className={`w-full h-11 px-4 rounded-lg bg-surface-container-low text-body-md text-on-surface placeholder-on-surface-variant focus:outline-none focus:bg-surface-container focus:ring-2 focus:ring-primary/30 transition-all ${fieldErrors.email ? 'border-2 border-error' : 'border border-transparent'}`}
              />
              {fieldErrors.email && <p className="font-body-sm text-body-sm text-error">{fieldErrors.email}</p>}
            </div>

            {/* Phone */}
            <div className="space-y-1.5">
              <label htmlFor="phone" className="font-label-md text-label-md text-on-surface font-medium flex items-center gap-1.5">
                <svg className="w-4 h-4 text-secondary" fill="currentColor" viewBox="0 0 20 20">
                  <path d="M2 3a1 1 0 011-1h2.153a1 1 0 01.986.836l.74 4.435a1 1 0 01-.54 1.06l-1.64.773c.636 1.879 2.42 3.537 4.228 4.184l.774-1.707a1 1 0 011.05-.528l4.434.74a1 1 0 01.836.986V17a1 1 0 01-1 1h-2C7.82 18 2 12.18 2 5V3z" />
                </svg>
                Phone
              </label>
              <input
                id="phone"
                type="tel"
                name="phone"
                value={form.phone}
                onChange={handleChange}
                required
                placeholder="+1 (555) 000-0000"
                className={`w-full h-11 px-4 rounded-lg bg-surface-container-low text-body-md text-on-surface placeholder-on-surface-variant focus:outline-none focus:bg-surface-container focus:ring-2 focus:ring-primary/30 transition-all ${fieldErrors.phone ? 'border-2 border-error' : 'border border-transparent'}`}
              />
              {fieldErrors.phone && <p className="font-body-sm text-body-sm text-error">{fieldErrors.phone}</p>}
            </div>

            {/* Password */}
            <div className="space-y-1.5">
              <label htmlFor="password" className="font-label-md text-label-md text-on-surface font-medium flex items-center gap-1.5">
                <svg className="w-4 h-4 text-secondary" fill="currentColor" viewBox="0 0 20 20">
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
                minLength={8}
                placeholder="••••••••"
                className={`w-full h-11 px-4 rounded-lg bg-surface-container-low text-body-md text-on-surface placeholder-on-surface-variant focus:outline-none focus:bg-surface-container focus:ring-2 focus:ring-primary/30 transition-all ${fieldErrors.password ? 'border-2 border-error' : 'border border-transparent'}`}
              />
              {form.password && (
                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="font-label-sm text-label-sm text-on-surface-variant">Password strength:</span>
                    <span className={`font-label-sm text-label-sm font-semibold ${strengthInfo.msg === 'Weak' ? 'text-error' : strengthInfo.msg === 'Fair' ? 'text-secondary' : strengthInfo.msg === 'Good' ? 'text-primary' : 'text-secondary-container'}`}>
                      {strengthInfo.msg}
                    </span>
                  </div>
                  <div className="h-2 bg-surface-container rounded-full overflow-hidden">
                    <div className={`h-full ${strengthInfo.bgClass} transition-all`} style={{ width: `${form.password.length > 12 ? 100 : (form.password.length / 12) * 100}%` }}></div>
                  </div>
                </div>
              )}
              <p className="font-label-sm text-label-sm text-on-surface-variant">
                Must include: uppercase, lowercase, number & special character (@#$%^&+=!)
              </p>
              {fieldErrors.password && <p className="font-body-sm text-body-sm text-error">{fieldErrors.password}</p>}
            </div>

            {/* Confirm Password */}
            <div className="space-y-1.5">
              <label htmlFor="confirmPassword" className="font-label-md text-label-md text-on-surface font-medium flex items-center gap-1.5">
                <svg className="w-4 h-4 text-secondary" fill="currentColor" viewBox="0 0 20 20">
                  <path fillRule="evenodd" d="M5 9V7a5 5 0 0110 0v2a2 2 0 012 2v5a2 2 0 01-2 2H5a2 2 0 01-2-2v-5a2 2 0 012-2zm8-2v2H7V7a3 3 0 016 0z" clipRule="evenodd" />
                </svg>
                Confirm Password
              </label>
              <input
                id="confirmPassword"
                type="password"
                name="confirmPassword"
                value={form.confirmPassword}
                onChange={handleChange}
                required
                placeholder="••••••••"
                className={`w-full h-11 px-4 rounded-lg bg-surface-container-low text-body-md text-on-surface placeholder-on-surface-variant focus:outline-none focus:bg-surface-container focus:ring-2 focus:ring-primary/30 transition-all ${fieldErrors.confirmPassword ? 'border-2 border-error' : 'border border-transparent'}`}
              />
              {fieldErrors.confirmPassword && <p className="font-body-sm text-body-sm text-error">{fieldErrors.confirmPassword}</p>}
            </div>

            {/* Submit Button */}
            <button
              type="submit"
              disabled={loading}
              className="w-full h-12 rounded-lg bg-primary text-on-primary font-label-lg text-label-lg font-semibold hover:bg-primary-container disabled:opacity-50 disabled:cursor-not-allowed transition-all flex items-center justify-center gap-2 mt-2"
            >
              {loading ? (
                <>
                  <svg className="w-5 h-5 animate-spin" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                  </svg>
                  Creating account...
                </>
              ) : (
                <>
                  <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
                    <path d="M8 16.5a1.5 1.5 0 11-3 0 1.5 1.5 0 013 0zM15 16.5a1.5 1.5 0 11-3 0 1.5 1.5 0 013 0z" />
                    <path d="M3 4a1 1 0 00-1 1v10a1 1 0 001 1h1.05a2.5 2.5 0 014.9 0H10a1 1 0 001-1V5a1 1 0 00-1-1H3zM14 7a1 1 0 00-1 1v6.05A2.5 2.5 0 0115.95 16H17a1 1 0 001-1v-5a1 1 0 00-.293-.707l-2-2A1 1 0 0015 7h-1z" />
                  </svg>
                  Create Account
                </>
              )}
            </button>
          </form>
        </div>

        {/* Login Link */}
        <div className="text-center mt-6">
          <p className="font-body-md text-body-md text-on-surface-variant">
            Already have an account?{' '}
            <Link
              to="/login"
              className="text-primary font-semibold hover:text-primary-container transition-colors"
            >
              Sign in
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

export default RegisterPage;
