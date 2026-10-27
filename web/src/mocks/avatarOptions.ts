import type { PetType } from "../types/customer";

export interface AvatarOption {
  id: string;
  label: string;
  petType: PetType;
  url: string;
}

export const AVATAR_OPTIONS: AvatarOption[] = [
  { id: "golden-retriever", label: "Golden Retriever", petType: "DOG", url: "/images/avatars/golden-retriever.webp" },
  { id: "border-collie", label: "Border Collie", petType: "DOG", url: "/images/avatars/border-collie.webp" },
  { id: "buldogue-frances", label: "Buldogue Francês", petType: "DOG", url: "/images/avatars/buldogue-frances.webp" },
  { id: "shiba-inu", label: "Shiba Inu", petType: "DOG", url: "/images/avatars/shiba-inu.webp" },
  { id: "dachshund", label: "Dachshund", petType: "DOG", url: "/images/avatars/dachshund.webp" },
  { id: "gato-siames", label: "Siamês", petType: "CAT", url: "/images/avatars/gato-siames.webp" },
  { id: "gato-laranja", label: "Laranja", petType: "CAT", url: "/images/avatars/gato-laranja.webp" },
  { id: "gato-preto", label: "Preto", petType: "CAT", url: "/images/avatars/gato-preto.webp" },
  { id: "gato-british-shorthair", label: "British Shorthair", petType: "CAT", url: "/images/avatars/gato-british-shorthair.webp" },
];
