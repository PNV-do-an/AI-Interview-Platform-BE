import React from 'react';
import { ExperienceData } from '../../models/Cv.model';

interface ExperienceTimelineProps {
  experiences: ExperienceData[];
}

export const ExperienceTimeline: React.FC<ExperienceTimelineProps> = ({ experiences }) => {
  if (!experiences || experiences.length === 0) {
    return (
      <div className="bg-surface-container-lowest rounded-xl p-space-lg shadow-sm text-center py-12">
        <p className="font-body-md text-body-md text-on-surface-variant">
          No work experience found. Upload a CV to see your experience timeline.
        </p>
      </div>
    );
  }

  return (
    <div className="bg-surface-container-lowest rounded-xl p-space-lg shadow-sm flex flex-col gap-space-md">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <svg className="w-6 h-6 text-primary" fill="currentColor" viewBox="0 0 20 20">
            <path d="M9 2a1 1 0 000 2h2a1 1 0 100-2H9z" />
            <path fillRule="evenodd" d="M4 5a2 2 0 012-2 1 1 0 000 2H6a6 6 0 016 6v3a3 3 0 01-3 3H7a1 1 0 100 2h2a5 5 0 005-5v-3a8 8 0 00-8-8H6a1 1 0 000 2h1a2 2 0 01-2-2V5zm5 9a1 1 0 100-2 1 1 0 000 2z" clipRule="evenodd" />
          </svg>
          <h3 className="font-title-lg text-title-lg text-on-surface font-bold">Extracted Experience Timeline</h3>
        </div>
        <span className="font-code text-code text-on-surface-variant">{experiences.length} Position{experiences.length !== 1 ? 's' : ''}</span>
      </div>

      <div className="relative pl-6 space-y-6 before:content-[''] before:absolute before:left-2 before:top-2 before:bottom-2 before:w-0.5 before:bg-surface-container">
        {experiences.map((exp, index) => (
          <div key={index} className="relative flex flex-col gap-1.5">
            <div className="absolute -left-6 top-1.5 w-4 h-4 rounded-full bg-primary-container flex items-center justify-center ring-4 ring-surface-container-lowest">
              <span className="w-1.5 h-1.5 rounded-full bg-white"></span>
            </div>

            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-1">
              <span className="font-label-lg text-label-lg text-on-surface font-bold">
                {exp.position}
              </span>
              <span className="font-code text-code text-on-surface-variant">
                {exp.startDate} — {exp.endDate || 'Present'}
              </span>
            </div>

            <span className="font-body-sm text-body-sm text-secondary font-medium">
              {exp.company} {exp.location ? `• ${exp.location}` : ''}
            </span>

            {exp.description && (
              <p className="font-body-md text-body-md text-on-surface-variant">
                {exp.description}
              </p>
            )}

            {exp.technologies && exp.technologies.length > 0 && (
              <div className="flex flex-wrap gap-1.5 pt-1">
                {exp.technologies.map((tech, i) => (
                  <span
                    key={i}
                    className="px-2 py-0.5 rounded bg-surface-container-low font-code text-[11px] text-on-surface-variant"
                  >
                    {tech}
                  </span>
                ))}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
};
