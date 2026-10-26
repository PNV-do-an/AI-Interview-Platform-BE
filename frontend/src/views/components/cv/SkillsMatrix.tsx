import React from 'react';
import { SkillData, ProficiencyLevel } from '../../../models/Cv.model';

interface SkillsMatrixProps {
  skills: SkillData[];
}

const categoryMap: Record<ProficiencyLevel, string> = {
  [ProficiencyLevel.BEGINNER]: 'Foundational',
  [ProficiencyLevel.INTERMEDIATE]: 'Working',
  [ProficiencyLevel.ADVANCED]: 'Proficient',
  [ProficiencyLevel.EXPERT]: 'Expert',
};

const proficiencyColors: Record<ProficiencyLevel, { bg: string; text: string }> = {
  [ProficiencyLevel.BEGINNER]: { bg: 'bg-surface-container', text: 'text-on-surface-variant' },
  [ProficiencyLevel.INTERMEDIATE]: { bg: 'bg-secondary-container', text: 'text-on-secondary' },
  [ProficiencyLevel.ADVANCED]: { bg: 'bg-tertiary-container', text: 'text-on-tertiary' },
  [ProficiencyLevel.EXPERT]: { bg: 'bg-primary-container', text: 'text-on-primary' },
};

const SkillsMatrix: React.FC<SkillsMatrixProps> = ({ skills }) => {
  if (!skills || skills.length === 0) {
    return <p className="text-on-surface-variant">No skills available</p>;
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-outline-variant">
            <th className="text-left py-3 px-4 font-label-lg text-label-lg text-on-surface">Skill Name</th>
            <th className="text-left py-3 px-4 font-label-lg text-label-lg text-on-surface">Category</th>
            <th className="text-left py-3 px-4 font-label-lg text-label-lg text-on-surface">Experience (Years)</th>
            <th className="text-left py-3 px-4 font-label-lg text-label-lg text-on-surface">Proficiency Tier</th>
          </tr>
        </thead>
        <tbody>
          {skills.map((skill, index) => (
            <tr key={index} className="border-b border-outline-variant hover:bg-surface-container-low transition-colors">
              <td className="py-3 px-4 text-on-surface font-label-md">{skill.name}</td>
              <td className="py-3 px-4 text-on-surface-variant font-label-md">{categoryMap[skill.proficiency]}</td>
              <td className="py-3 px-4 text-on-surface font-label-md">{skill.years}</td>
              <td className="py-3 px-4">
                <span
                  className={`inline-block px-3 py-1 rounded-full text-label-sm font-label-sm ${proficiencyColors[skill.proficiency].bg} ${proficiencyColors[skill.proficiency].text}`}
                >
                  {categoryMap[skill.proficiency]}
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

export default SkillsMatrix;
