const FALLBACK_BY_TYPE: Record<"DOG" | "CAT", string> = {
  DOG: "/images/avatars/golden-retriever.webp",
  CAT: "/images/avatars/gato-laranja.webp",
};

interface PetAvatarProps {
  name: string;
  petType: "DOG" | "CAT";
  avatarUrl?: string;
  size?: number;
}

export function PetAvatar({ name, petType, avatarUrl, size = 72 }: PetAvatarProps) {
  const src = avatarUrl ?? FALLBACK_BY_TYPE[petType];
  return <img className="pet-avatar" src={src} alt={name} width={size} height={size} loading="lazy" />;
}
