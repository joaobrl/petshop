// Mirrors customer-management's PetResponseDto/PetRequestDto and CustomerResponseDto (api/rest/dto)
export type PetType = "DOG" | "CAT";
export type SizeCategory = "SMALL" | "MEDIUM" | "LARGE";

export interface Pet {
  id: string;
  petName: string;
  petType: PetType;
  petBreed: string;
  petSize: SizeCategory;
  weightInKg: number;
  petHealthIssues: string;
}

export interface PetRequest {
  petName: string;
  petType: PetType;
  petBreed: string;
  petSize: SizeCategory;
  weightInKg: number;
  petHealthIssues: string;
}

export interface Customer {
  id: string;
  name: string;
  cpf: string;
  email: string;
  phone: string;
  enabled: boolean;
  pets: Pet[];
}

export interface CustomerRegistrationRequest {
  name: string;
  cpf: string;
  email: string;
  phone: string;
  password: string;
  confirmPassword: string;
}
