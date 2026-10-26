import React, { useRef } from 'react';

interface UploadZoneProps {
  onFile: (file: File) => void;
  loading?: boolean;
}

const UploadZone: React.FC<UploadZoneProps> = ({ onFile, loading = false }) => {
  const inputRef = useRef<HTMLInputElement>(null);
  const [isDragActive, setIsDragActive] = React.useState(false);

  const handleDrag = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === 'dragenter' || e.type === 'dragover') {
      setIsDragActive(true);
    } else if (e.type === 'dragleave') {
      setIsDragActive(false);
    }
  };

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragActive(false);

    const files = e.dataTransfer.files;
    if (files && files[0]) {
      const file = files[0];
      if (file.type === 'application/pdf' || file.type === 'text/plain') {
        onFile(file);
      }
    }
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      onFile(e.target.files[0]);
    }
  };

  const handleClick = () => {
    inputRef.current?.click();
  };

  return (
    <div
      onDragEnter={handleDrag}
      onDragLeave={handleDrag}
      onDragOver={handleDrag}
      onDrop={handleDrop}
      onClick={handleClick}
      className={`border-2 border-dashed rounded-lg p-8 text-center cursor-pointer transition-colors ${
        isDragActive
          ? 'border-primary bg-primary-fixed'
          : 'border-outline-variant bg-surface-container-low hover:bg-surface-container'
      } ${loading ? 'opacity-50 cursor-not-allowed' : ''}`}
    >
      <input
        ref={inputRef}
        type="file"
        accept=".pdf,.txt"
        onChange={handleChange}
        className="hidden"
        disabled={loading}
      />

      {loading ? (
        <div className="flex flex-col items-center gap-2">
          <div className="animate-spin">
            <span className="material-symbols-outlined text-4xl text-primary">hourglass_empty</span>
          </div>
          <p className="text-on-surface font-label-md">Uploading...</p>
        </div>
      ) : (
        <div className="flex flex-col items-center gap-3">
          <span className="material-symbols-outlined text-4xl text-outline">description</span>
          <div>
            <p className="font-label-lg text-label-lg text-on-surface">Drag & drop or click to browse</p>
            <p className="text-body-sm text-on-surface-variant">Supports PDF and TXT files</p>
          </div>
        </div>
      )}
    </div>
  );
};

export default UploadZone;
