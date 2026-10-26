import React from 'react';
import { ExperienceData } from '../../../models/Cv.model';

interface ExperienceTimelineProps {
  experience: ExperienceData[];
}

const ExperienceTimeline: React.FC<ExperienceTimelineProps> = ({ experience }) => {
  if (!experience || experience.length === 0) {
    return <p className="text-on-surface-variant">No experience data available</p>;
  }

  return (
    <div className="space-y-4">
      {experience.map((exp, index) => (
        <div key={index} className="relative pl-6">
          {/* Timeline dot */}
          <div className="absolute left-0 top-2 w-3 h-3 rounded-full bg-primary border-2 border-primary-fixed" />

          {/* Timeline line */}
          {index < experience.length - 1 && (
            <div className="absolute left-1 top-5 w-0.5 h-12 bg-outline-variant" />
          )}

          {/* Content */}
          <div className="p-4 rounded-lg bg-surface-container-low border border-outline-variant">
            <div className="flex items-start justify-between mb-2">
              <div>
                <h4 className="text-title-md font-title-md text-on-surface">{exp.role}</h4>
                <p className="text-label-md text-on-surface-variant">{exp.company}</p>
              </div>
              <span className="text-label-sm text-on-surface-variant whitespace-nowrap ml-4">{exp.duration}</span>
            </div>

            {exp.achievements && exp.achievements.length > 0 && (
              <ul className="mt-3 space-y-1">
                {exp.achievements.map((achievement, idx) => (
                  <li key={idx} className="text-body-sm text-on-surface-variant flex items-start gap-2">
                    <span className="text-primary mt-1">•</span>
                    <span>{achievement}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      ))}
    </div>
  );
};

export default ExperienceTimeline;
