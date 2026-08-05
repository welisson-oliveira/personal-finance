export interface Category {
  id: string;
  name: string;
  icon?: string;
  color?: string;
  global: boolean;
  parentId?: string | null;
  parentName?: string | null;
}
