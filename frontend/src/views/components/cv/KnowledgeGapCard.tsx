import React from 'react';

interface KnowledgeGapCardProps {
  gap: string;
  index: number;
  onAddToStudyPlan?: (gap: string) => void;
}

const KnowledgeGapCard: React.FC<KnowledgeGapCardProps> = ({ gap, index, onAddToStudyPlan }) => {
  const priority = index < 3 ? 'HIGH' : 'MEDIUM';
  const priorityColor = priority === 'HIGH' ? 'bg-error text-on-error' : 'bg-secondary-container text-on-secondary';

  return (
    <div className="p-4 rounded-lg border border-outline-variant bg-surface-container-low hover:bg-surface-container transition-colors">
      <div className="flex items-start justify-between gap-3">
        <div className="flex-1">
          <div className="flex items-center gap-2 mb-2">
            <span className={`text-label-sm font-label-sm px-2 py-1 rounded ${priorityColor}`}>
              {priority}
            </span>
          </div>
          <p className="text-body-md text-on-surface">{gap}</p>
        </div>
        {onAddToStudyPlan && (
          <button
            onClick={() => onAddToStudyPlan(gap)}
            className="px-3 py-1 text-label-md font-label-md text-primary hover:bg-primary-fixed rounded transition-colors whitespace-nowrap"
          >
            Add to Plan
          </button>
        )}
      </div>
    </div>
  );
};

export default KnowledgeGapCard;
