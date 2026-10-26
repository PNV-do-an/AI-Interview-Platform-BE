import React from 'react';
import { PositionFit } from '../../models/Cv.model';

interface PositionFitCardProps {
  fit: PositionFit;
  onCalibrate?: () => void;
}

const getMatchColor = (percentage: number) => {
  if (percentage >= 90) return 'bg-primary-container text-on-primary';
  if (percentage >= 80) return 'bg-secondary text-on-secondary';
  return 'bg-surface-container-highest text-on-surface';
};

export const PositionFitCard: React.FC<PositionFitCardProps> = ({ fit, onCalibrate }) => {
  return (
    <div className="bg-surface-container-low p-3 rounded-xl flex items-center justify-between hover:bg-surface-container transition-colors">
      <div className="flex items-center gap-3">
        <div className={`w-9 h-9 rounded-lg ${getMatchColor(fit.matchPercentage)} flex items-center justify-center font-bold font-code text-xs`}>
          {fit.matchPercentage}%
        </div>
        <div className="flex flex-col">
          <span className="font-label-lg text-label-lg text-on-surface font-semibold">
            {fit.position}
          </span>
          <span className="font-label-sm text-label-sm text-on-surface-variant">
            {fit.description || 'Core Services • High-Scale Systems'}
          </span>
        </div>
      </div>
      <button
        onClick={onCalibrate}
        className="px-2.5 py-1 rounded-lg bg-surface-container-lowest text-primary hover:bg-primary-container hover:text-on-primary font-label-sm text-label-sm transition-all shadow-sm"
      >
        Calibrate
      </button>
    </div>
  );
};
