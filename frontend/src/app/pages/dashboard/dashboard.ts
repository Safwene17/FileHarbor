import { Component, OnInit, ElementRef, ViewChild, inject, signal, computed } from '@angular/core';

import { CommonModule } from '@angular/common';
import { FileService } from '../../services/file';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard implements OnInit {
  private fileService = inject(FileService);

  // Theme preference
  isDarkMode = signal(localStorage.getItem('fileharbor-theme') !== 'light');

  toggleTheme(): void {
    this.isDarkMode.update((dark) => !dark);

    localStorage.setItem('fileharbor-theme', this.isDarkMode() ? 'dark' : 'light');
  }

  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  // Reactive state
  files = signal<string[]>([]);
  selectedFile = signal<File | null>(null);

  loading = signal(false);
  uploading = signal(false);
  deleting = signal(false);

  error = signal('');
  success = signal('');

  deletingFile = signal<string | null>(null);
  // Search, filter and sorting
  searchQuery = signal('');

  selectedType = signal('all');

  sortOrder = signal('az');

  filteredFiles = computed(() => {
    const query = this.searchQuery().toLowerCase().trim();
    const type = this.selectedType();
    const order = this.sortOrder();

    return this.files()
      .filter((filename) => {
        const name = filename.toLowerCase();
        const extension = name.split('.').pop() ?? '';

        const matchesSearch = name.includes(query);

        const imageTypes = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp'];
        const documentTypes = ['doc', 'docx', 'txt', 'rtf', 'odt'];
        const spreadsheetTypes = ['xls', 'xlsx', 'csv', 'ods'];
        const videoTypes = ['mp4', 'mov', 'avi', 'mkv', 'webm'];
        const audioTypes = ['mp3', 'wav', 'ogg', 'm4a', 'flac'];

        let matchesType = true;

        switch (type) {
          case 'images':
            matchesType = imageTypes.includes(extension);
            break;
          case 'pdf':
            matchesType = extension === 'pdf';
            break;
          case 'documents':
            matchesType = documentTypes.includes(extension);
            break;
          case 'spreadsheets':
            matchesType = spreadsheetTypes.includes(extension);
            break;
          case 'videos':
            matchesType = videoTypes.includes(extension);
            break;
          case 'audio':
            matchesType = audioTypes.includes(extension);
            break;
          case 'other':
            matchesType = ![
              ...imageTypes,
              'pdf',
              ...documentTypes,
              ...spreadsheetTypes,
              ...videoTypes,
              ...audioTypes,
            ].includes(extension);
            break;
        }

        return matchesSearch && matchesType;
      })
      .sort((a, b) => {
        return order === 'az' ? a.localeCompare(b) : b.localeCompare(a);
      });
  });

  ngOnInit(): void {
    this.loadFiles();
  }

  // Load files from the backend
  loadFiles(): void {
    this.loading.set(true);
    this.error.set('');

    this.fileService.getFiles().subscribe({
      next: (files) => {
        this.files.set(files);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Error loading files:', err);

        this.error.set('Unable to load files. Please try again.');
        this.loading.set(false);
      },
    });
  }

  // Select a file
  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;

    this.selectedFile.set(input.files?.[0] ?? null);
    this.error.set('');
    this.success.set('');
  }

  // Upload a file
  uploadFile(): void {
    const file = this.selectedFile();

    if (!file || this.uploading()) return;

    this.uploading.set(true);
    this.error.set('');
    this.success.set('');

    const filename = file.name;

    this.fileService.uploadFile(file).subscribe({
      next: () => {
        this.selectedFile.set(null);
        this.fileInput.nativeElement.value = '';

        this.uploading.set(false);
        this.success.set(`${filename} uploaded successfully.`);

        // Reload the latest files from the backend
        this.loadFiles();
      },
      error: (err) => {
        console.error('Upload error:', err);

        this.error.set('Upload failed. Please try again.');
        this.uploading.set(false);
      },
    });
  }

  // Download a file
  downloadFile(filename: string): void {
    this.error.set('');
    this.success.set('');

    this.fileService.downloadFile(filename).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');

        link.href = url;
        link.download = filename;

        document.body.appendChild(link);
        link.click();
        link.remove();

        window.setTimeout(() => {
          window.URL.revokeObjectURL(url);
        }, 1000);
      },
      error: (err) => {
        console.error('Download error:', err);
        this.error.set('Download failed. Please try again.');
      },
    });
  }

  // Open delete confirmation modal
  requestDelete(filename: string): void {
    this.deletingFile.set(filename);
    this.error.set('');
    this.success.set('');
  }

  // Cancel deletion
  cancelDelete(): void {
    if (this.deleting()) return;

    this.deletingFile.set(null);
  }

  // Confirm deletion
  confirmDelete(): void {
    const filename = this.deletingFile();

    if (!filename || this.deleting()) return;

    this.deleting.set(true);
    this.error.set('');
    this.success.set('');

    this.fileService.deleteFile(filename).subscribe({
      next: () => {
        this.deleting.set(false);
        this.deletingFile.set(null);

        this.success.set(`${filename} deleted successfully.`);

        // Reload the latest files from the backend
        this.loadFiles();
      },
      error: (err) => {
        console.error('Delete error:', err);

        this.error.set('Delete failed. Please try again.');
        this.deleting.set(false);
        this.deletingFile.set(null);
      },
    });
  }
}
