import React from 'react';

interface PositionFitCardProps {
  position: string;
  rank: number;
  level?: string | null;
  matchPercentage?: number;
  requiredSkillCount?: number;
  matchedSkills?: string[];
  missingSkills?: string[];
}

const PositionFitCard: React.FC<PositionFitCardProps> = ({
  position,
  rank,
  level,
  matchPercentage,
  requiredSkillCount,
  matchedSkills = [],
  missingSkills = [],
}) => {
  const rankBadgeColor =
    rank === 1 ? 'bg-primary text-on-primary' : rank === 2 ? 'bg-secondary-container text-on-secondary' : 'bg-surface-container text-on-surface';

  const hasScore = typeof matchPercentage === 'number';
  const totalSkills = requiredSkillCount ?? matchedSkills.length + missingSkills.length;

  return (
    <div className="p-4 rounded-lg border border-outline-variant bg-surface-container-low hover:bg-surface-container transition-colors">
      <div className="flex items-start justify-between gap-3">
        <div className="flex-1">
          <h4 className="text-title-md font-title-md text-on-surface">{position}</h4>
          {level && (
            <p className="text-label-sm font-label-sm text-on-surface-variant mt-1">Level: {level}</p>
          )}
        </div>
        <div className="flex items-center gap-2">
          {hasScore && (
            <span className="text-title-md font-title-md text-primary font-semibold">{matchPercentage}%</span>
          )}
          <span className={`text-label-sm font-label-sm px-2 py-1 rounded ${rankBadgeColor}`}>
            #{rank}
          </span>
        </div>
      </div>

      {hasScore && (
        <div className="mt-3 flex items-center gap-3">
          <div className="flex-1 h-1.5 bg-surface-container rounded-full overflow-hidden">
            <div
              className="h-full bg-primary rounded-full transition-all"
              style={{ width: `${matchPercentage}%` }}
            />
          </div>
          <span className="text-label-sm font-label-sm text-on-surface-variant whitespace-nowrap">
            {matchedSkills.length}/{totalSkills} skills
          </span>
        </div>
      )}

      {(matchedSkills.length > 0 || missingSkills.length > 0) && (
        <div className="mt-3 flex flex-wrap gap-1.5">
          {matchedSkills.map((skill) => (
            <span
              key={`matched-${skill}`}
              className="px-2 py-0.5 rounded-full text-label-sm font-label-sm bg-secondary-container text-on-secondary"
            >
              {skill}
            </span>
          ))}
          {missingSkills.map((skill) => (
            <span
              key={`missing-${skill}`}
              className="px-2 py-0.5 rounded-full text-label-sm font-label-sm bg-surface-container text-on-surface-variant border border-outline-variant"
            >
              {skill}
            </span>
          ))}
        </div>
      )}
    </div>
  );
};

export default PositionFitCard;
