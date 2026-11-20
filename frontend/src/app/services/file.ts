
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class FileService {

  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;

  // Get all files
  getFiles(): Observable<string[]> {
    return this.http.get<string[]>(this.apiUrl);
  }

  // Upload a file
  uploadFile(file: File): Observable<string> {
    const formData = new FormData();
    formData.append('file', file);

    return this.http.post(this.apiUrl, formData, {
      responseType: 'text'
    });
  }

  // Download a file
  downloadFile(filename: string): Observable<Blob> {
    return this.http.get(
      `${this.apiUrl}/${encodeURIComponent(filename)}`,
      { responseType: 'blob' }
    );
  }

  // Delete a file
  deleteFile(filename: string): Observable<string> {
    return this.http.delete(
      `${this.apiUrl}/${encodeURIComponent(filename)}`,
      { responseType: 'text' }
    );
  }
}
