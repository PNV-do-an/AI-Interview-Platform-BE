import React from 'react';
import { SkillData } from '../../../models/Cv.model';

interface EvaluationDimensionsProps {
  skills: SkillData[];
}

const EvaluationDimensions: React.FC<EvaluationDimensionsProps> = ({ skills }) => {
  const counts = {
    BEGINNER: skills.filter(s => s.proficiency === 'BEGINNER').length,
    INTERMEDIATE: skills.filter(s => s.proficiency === 'INTERMEDIATE').length,
    ADVANCED: skills.filter(s => s.proficiency === 'ADVANCED').length,
    EXPERT: skills.filter(s => s.proficiency === 'EXPERT').length,
  };

  const dimensions = [
    { label: 'Foundational', count: counts.BEGINNER, color: 'bg-surface-container text-on-surface-variant' },
    { label: 'Working', count: counts.INTERMEDIATE, color: 'bg-secondary-container text-on-secondary' },
    { label: 'Proficient', count: counts.ADVANCED, color: 'bg-tertiary-container text-on-tertiary' },
    { label: 'Expert', count: counts.EXPERT, color: 'bg-primary-container text-on-primary' },
  ];

  return (
    <div className="space-y-3">
      <h4 className="text-title-md font-title-md text-on-surface">Skill Levels</h4>
      {dimensions.map((dim, index) => (
        <div key={index} className="flex items-center gap-3">
          <div className={`w-12 h-12 rounded-full flex items-center justify-center font-label-lg ${dim.color}`}>
            {dim.count}
          </div>
          <span className="text-label-md text-on-surface-variant flex-1">{dim.label}</span>
        </div>
      ))}
    </div>
  );
};

export default EvaluationDimensions;
