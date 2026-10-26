import React from 'react';
import { SkillData, ProficiencyLevel } from '../../models/Cv.model';

interface SkillsMatrixProps {
  skills: SkillData[];
  onFilter?: (text: string) => void;
}

const getProficiencyColor = (level: ProficiencyLevel): string => {
  switch (level) {
    case ProficiencyLevel.EXPERT:
      return 'bg-tertiary-fixed text-on-tertiary-fixed';
    case ProficiencyLevel.ADVANCED:
      return 'bg-surface-container-high text-primary';
    case ProficiencyLevel.INTERMEDIATE:
      return 'bg-surface-container text-on-surface-variant';
    default:
      return 'bg-surface-container-low text-on-surface-variant';
  }
};

const getProficiencyLabel = (level: ProficiencyLevel): string => {
  switch (level) {
    case ProficiencyLevel.EXPERT:
      return 'Expert Tier';
    case ProficiencyLevel.ADVANCED:
      return 'Advanced Tier';
    case ProficiencyLevel.INTERMEDIATE:
      return 'Intermediate';
    default:
      return 'Beginner';
  }
};

export const SkillsMatrix: React.FC<SkillsMatrixProps> = ({ skills, onFilter }) => {
  const [filterText, setFilterText] = React.useState('');

  const filteredSkills = skills.filter(skill =>
    skill.name.toLowerCase().includes(filterText.toLowerCase())
  );

  const handleFilterChange = (text: string) => {
    setFilterText(text);
    onFilter?.(text);
  };

  if (skills.length === 0) {
    return (
      <div className="bg-surface-container-lowest rounded-xl p-space-lg shadow-sm text-center py-12">
        <p className="font-body-md text-body-md text-on-surface-variant">
          No skills extracted yet. Upload or parse a CV to see extracted skills.
        </p>
      </div>
    );
  }

  return (
    <div className="bg-surface-container-lowest rounded-xl p-space-lg shadow-sm flex flex-col gap-space-md">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <svg className="w-6 h-6 text-primary" fill="currentColor" viewBox="0 0 20 20">
            <path d="M3 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm0 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm0 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zm0 4a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1z" />
          </svg>
          <span className="font-title-md text-title-md text-on-surface font-bold">Parsed Technical Skills Matrix</span>
          <span className="px-2.5 py-0.5 rounded-full bg-surface-container font-label-sm text-label-sm text-primary font-semibold">
            {skills.length} Identified
          </span>
        </div>

        <div className="flex items-center gap-2">
          <input
            type="text"
            placeholder="Filter extracted skills..."
            value={filterText}
            onChange={(e) => handleFilterChange(e.target.value)}
            className="h-9 px-3 rounded-lg bg-surface-container-low font-body-sm text-body-sm text-on-surface focus:outline-none focus:bg-surface-container"
          />
        </div>
      </div>

      <div className="overflow-x-auto rounded-lg">
        <table className="w-full text-left">
          <thead>
            <tr className="bg-surface-container-low text-on-surface-variant font-label-sm text-label-sm uppercase tracking-wider">
              <th className="py-3 px-4 rounded-l-lg">Skill / Technology</th>
              <th className="py-3 px-4">Category</th>
              <th className="py-3 px-4">Tenure</th>
              <th className="py-3 px-4">Proficiency</th>
              <th className="py-3 px-4 rounded-r-lg text-right">Status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-transparent font-body-md text-body-md text-on-surface">
            {filteredSkills.map((skill) => (
              <tr key={skill.id} className="hover:bg-surface-container-low transition-colors">
                <td className="py-3.5 px-4 font-semibold flex items-center gap-2">
                  <span className="h-2 w-2 rounded-full bg-primary"></span>
                  {skill.name}
                </td>
                <td className="py-3.5 px-4 text-on-surface-variant font-body-sm text-body-sm">
                  {skill.category || 'General'}
                </td>
                <td className="py-3.5 px-4 font-code text-code">
                  {skill.years ? `${skill.years} Years` : 'N/A'}
                </td>
                <td className="py-3.5 px-4">
                  <span className={`px-3 py-1 rounded-full font-label-sm text-label-sm font-semibold ${getProficiencyColor(skill.proficiency)}`}>
                    {getProficiencyLabel(skill.proficiency)}
                  </span>
                </td>
                <td className="py-3.5 px-4 text-right">
                  <span className="inline-flex items-center gap-1 text-secondary font-label-sm text-label-sm font-medium">
                    <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
                      <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                    </svg>
                    Verified
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {filteredSkills.length === 0 && (
        <div className="text-center py-8">
          <p className="font-body-md text-body-md text-on-surface-variant">
            No skills match your filter. Try a different search term.
          </p>
        </div>
      )}
    </div>
  );
};
