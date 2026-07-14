export interface KnownPerson {
  id: string;
  name: string;
  relationship: string;
  defaultTreatment: string;
  defaultLabel?: string;
  active: boolean;
}

export interface CreateKnownPersonRequest {
  name: string;
  relationship: string;
  defaultTreatment: string;
  defaultLabel?: string;
}
