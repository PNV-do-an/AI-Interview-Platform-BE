import React from 'react';

interface PositionFitCardProps {
  position: string;
  matchPercent: number;
  rank: number;
}

const PositionFitCard: React.FC<PositionFitCardProps> = ({ position, matchPercent, rank }) => {
  const rankBadgeColor =
    rank === 1 ? 'bg-primary text-on-primary' : rank === 2 ? 'bg-secondary-container text-on-secondary' : 'bg-surface-container text-on-surface';

  return (
    <div className="p-4 rounded-lg border border-outline-variant bg-surface-container-low hover:bg-surface-container transition-colors">
      <div className="flex items-start justify-between gap-3 mb-3">
        <h4 className="text-title-md font-title-md text-on-surface flex-1">{position}</h4>
        <span className={`text-label-sm font-label-sm px-2 py-1 rounded ${rankBadgeColor}`}>
          #{rank}
        </span>
      </div>

      <div className="space-y-2">
        <div className="flex items-center justify-between mb-1">
          <span className="text-label-md text-on-surface-variant">Match Score</span>
          <span className="text-label-lg font-label-lg text-primary">{matchPercent}%</span>
        </div>
        <div className="w-full bg-surface-container-low rounded-full h-2 overflow-hidden">
          <div
            className="bg-primary h-full transition-all"
            style={{ width: `${matchPercent}%` }}
          />
        </div>
      </div>
    </div>
  );
};

export default PositionFitCard;
