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
  return (
    <img
      src={src}
      alt={name}
      style={{
        width: size,
        height: size,
        borderRadius: "var(--radius-full)",
        objectFit: "cover",
        boxShadow: "0 0 0 3px var(--surface-raised), 0 0 0 4px var(--border)",
      }}
    />
  );
}
