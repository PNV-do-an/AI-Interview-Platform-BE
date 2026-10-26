import React, { useRef } from 'react';

interface CVUploadZoneProps {
  onFileSelected: (file: File) => void;
  isLoading?: boolean;
}

export const CVUploadZone: React.FC<CVUploadZoneProps> = ({ onFileSelected, isLoading = false }) => {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [isDragging, setIsDragging] = React.useState(false);

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = () => {
    setIsDragging(false);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    const files = e.dataTransfer.files;
    if (files.length > 0) {
      handleFileSelect(files[0]);
    }
  };

  const handleFileSelect = (file: File) => {
    const validTypes = ['application/pdf', 'text/plain'];
    const validNames = file.name.toLowerCase().endsWith('.pdf') || 
                      file.name.toLowerCase().endsWith('.txt');
    
    if (!validTypes.includes(file.type) && !validNames) {
      alert('Only PDF and TXT files are supported');
      return;
    }

    if (file.size > 10 * 1024 * 1024) {
      alert('File size must be less than 10MB');
      return;
    }

    onFileSelected(file);
  };

  return (
    <div
      className={`relative rounded-xl p-8 bg-surface-container-low flex flex-col items-center justify-center text-center cursor-pointer transition-all ${
        isDragging ? 'bg-surface-container' : 'hover:bg-surface-container'
      }`}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
    >
      <input
        ref={fileInputRef}
        type="file"
        accept=".pdf,.txt"
        className="absolute inset-0 opacity-0 cursor-pointer w-full h-full"
        onChange={(e) => {
          if (e.target.files?.[0]) {
            handleFileSelect(e.target.files[0]);
          }
        }}
        disabled={isLoading}
      />

      <div className="w-14 h-14 rounded-2xl bg-surface-container-highest flex items-center justify-center text-primary mb-3 group-hover:scale-105 transition-transform">
        <svg className="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M9 19l3 3m0 0l3-3m-3 3V10" />
        </svg>
      </div>

      <p className="font-title-md text-title-md text-on-surface font-semibold mb-1">
        Drag & drop your resume here, or <span className="text-primary underline">browse files</span>
      </p>

      <p className="font-body-sm text-body-sm text-on-surface-variant mb-4">
        Supports PDF and plain-text TXT (Maximum: 10 MB)
      </p>

      <div className="flex items-center gap-3">
        <span className="px-2.5 py-1 rounded-full bg-surface-container-highest font-code text-code text-on-surface-variant flex items-center gap-1">
          <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
            <path d="M4 3a2 2 0 00-2 2v10a2 2 0 002 2h12a2 2 0 002-2V5a2 2 0 00-2-2H4zm12 12H4l4-8 3 6 2-4 3 6z" />
          </svg>
          PDF OCR v2.1
        </span>
        <span className="px-2.5 py-1 rounded-full bg-surface-container-highest font-code text-code text-on-surface-variant flex items-center gap-1">
          <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
            <path d="M4 4a2 2 0 012-2h4l4 4v8a2 2 0 01-2 2H6a2 2 0 01-2-2V4z" />
          </svg>
          UTF-8 Text
        </span>
      </div>

      {isLoading && <div className="mt-4 text-primary text-sm font-medium">Uploading...</div>}
    </div>
  );
};
