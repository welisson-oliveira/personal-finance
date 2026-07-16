import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Category } from '../models/category.model';

export interface CreateCategoryRequest {
  name: string;
  icon?: string;
  color?: string;
  parentId?: string | null;
}

@Injectable({ providedIn: 'root' })
export class CategoryService {
  constructor(private http: HttpClient) {}

  getAll(): Observable<Category[]> {
    return this.http.get<Category[]>('/api/categories');
  }

  create(req: CreateCategoryRequest): Observable<Category> {
    return this.http.post<Category>('/api/categories', req);
  }

  update(id: string, req: CreateCategoryRequest): Observable<Category> {
    return this.http.put<Category>(`/api/categories/${id}`, req);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`/api/categories/${id}`);
  }
}
