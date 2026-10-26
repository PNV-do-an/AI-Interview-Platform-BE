import React from 'react';
import { KnowledgeGap } from '../../models/Cv.model';

interface KnowledgeGapCardProps {
  gap: KnowledgeGap;
  onAddToStudyPlan?: () => void;
}

const getPriorityStyles = (priority: string) => {
  switch (priority) {
    case 'HIGH':
      return {
        badge: 'bg-error-container text-on-error-container',
        dot: 'bg-error',
        icon: 'text-error',
      };
    case 'MEDIUM':
      return {
        badge: 'bg-surface-container text-on-surface-variant',
        dot: 'bg-secondary',
        icon: 'text-secondary',
      };
    default:
      return {
        badge: 'bg-surface-container text-on-surface-variant',
        dot: 'bg-surface-container-highest',
        icon: 'text-on-surface-variant',
      };
  }
};

export const KnowledgeGapCard: React.FC<KnowledgeGapCardProps> = ({ gap, onAddToStudyPlan }) => {
  const styles = getPriorityStyles(gap.priority);

  return (
    <div className="bg-surface-container-lowest rounded-xl p-space-md shadow-sm flex flex-col justify-between gap-3 relative overflow-hidden">
      <div className="flex items-start justify-between gap-2">
        <div className="flex items-center gap-2 text-on-surface font-semibold font-title-md text-title-md">
          <span className={`w-2.5 h-2.5 rounded-full ${styles.dot}`}></span>
          <span>{gap.title}</span>
        </div>
        <span className={`px-2 py-0.5 rounded-full ${styles.badge} font-label-sm text-label-sm font-semibold whitespace-nowrap`}>
          {gap.priority} Priority
        </span>
      </div>

      <p className="font-body-md text-body-md text-on-surface-variant">
        {gap.description}
      </p>

      <div className="flex items-center justify-between pt-2">
        <span className={`font-code text-code ${styles.icon} font-medium flex items-center gap-1`}>
          <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
            <path d="M10.894 2.553a1 1 0 00-1.788 0l-7 14a1 1 0 001.169 1.409l5.951-1.429 5.951 1.429a1 1 0 001.169-1.409l-7-14z" />
          </svg>
          Study Module Available
        </span>
        <button
          onClick={onAddToStudyPlan}
          className="px-3 py-1 rounded-lg bg-surface-container hover:bg-surface-container-high text-on-surface font-label-sm text-label-sm transition-colors"
        >
          Add to Study Plan
        </button>
      </div>
    </div>
  );
};
