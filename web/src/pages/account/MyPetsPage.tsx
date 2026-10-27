import { useEffect, useState } from "react";
import { useAuth } from "../../context/AuthContext";
import * as customerService from "../../services/customerService";
import { ApiError } from "../../services/http";
import { PetAvatar } from "../../components/ui/PetAvatar";
import { Button } from "../../components/ui/Button";
import { TextField, SelectField } from "../../components/ui/Field";
import { AVATAR_OPTIONS } from "../../mocks/avatarOptions";
import type { Customer, PetType, SizeCategory } from "../../types/customer";

export function MyPetsPage() {
  const { user } = useAuth();
  const [customer, setCustomer] = useState<Customer | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [name, setName] = useState("");
  const [avatarId, setAvatarId] = useState(AVATAR_OPTIONS[0].id);
  const [size, setSize] = useState<SizeCategory>("MEDIUM");
  const [weight, setWeight] = useState("");
  const [healthIssues, setHealthIssues] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function reload() {
    if (!user) return;
    customerService.getCustomer(user.id).then(setCustomer);
  }

  useEffect(reload, [user]);

  const selectedAvatar = AVATAR_OPTIONS.find((a) => a.id === avatarId)!;
  // The backend has no avatar concept — the picker just drives petType/petBreed;
  // the picture itself is matched back to a breed name for display (see PetAvatar).

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!user) return;
    setSaving(true);
    setError(null);
    try {
      const updated = await customerService.addPet(user.id, {
        petName: name,
        petType: selectedAvatar.petType as PetType,
        petBreed: selectedAvatar.label,
        petSize: size,
        weightInKg: Number(weight) || 0,
        petHealthIssues: healthIssues,
      });
      setCustomer(updated);
      setName("");
      setWeight("");
      setHealthIssues("");
      setShowForm(false);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Não foi possível salvar o pet.");
    } finally {
      setSaving(false);
    }
  }

  if (!customer) return <p className="body">Carregando…</p>;

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-6)", maxWidth: 560 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <h1 className="title">Meus pets</h1>
        <Button variant="outline" size="sm" onClick={() => setShowForm((v) => !v)}>
          {showForm ? "Cancelar" : "Adicionar pet"}
        </Button>
      </div>

      <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-3)" }}>
        {customer.pets.map((pet) => {
          const avatar = AVATAR_OPTIONS.find((a) => a.label === pet.petBreed);
          return (
            <div key={pet.id} className="card" style={{ display: "flex", alignItems: "center", gap: "var(--space-4)" }}>
              <PetAvatar name={pet.petName} petType={pet.petType} avatarUrl={avatar?.url} size={56} />
              <div>
                <p className="body-strong" style={{ margin: 0 }}>
                  {pet.petName}
                </p>
                <p className="caption" style={{ margin: "4px 0 0", color: "var(--ink-muted)" }}>
                  {pet.petBreed} · {pet.weightInKg}kg
                  {pet.petHealthIssues ? ` · ${pet.petHealthIssues}` : ""}
                </p>
              </div>
            </div>
          );
        })}
        {customer.pets.length === 0 && <p className="body">Nenhum pet cadastrado ainda.</p>}
      </div>

      {showForm && (
        <form onSubmit={handleSubmit} className="card" style={{ display: "flex", flexDirection: "column", gap: "var(--space-4)" }}>
          <span className="heading">Novo pet</span>

          <div>
            <span className="label" style={{ color: "var(--ink-muted)" }}>
              Escolha o avatar
            </span>
            <div style={{ display: "flex", gap: "var(--space-2)", flexWrap: "wrap", marginTop: "var(--space-2)" }}>
              {AVATAR_OPTIONS.map((option) => (
                <button
                  key={option.id}
                  type="button"
                  onClick={() => setAvatarId(option.id)}
                  title={option.label}
                  style={{
                    padding: 0,
                    border: "none",
                    background: "none",
                    cursor: "pointer",
                    borderRadius: "var(--radius-full)",
                    outline: avatarId === option.id ? "3px solid var(--secondary)" : "none",
                    outlineOffset: 2,
                  }}
                >
                  <img
                    src={option.url}
                    alt={option.label}
                    style={{ width: 48, height: 48, borderRadius: "var(--radius-full)", objectFit: "cover" }}
                  />
                </button>
              ))}
            </div>
          </div>

          <TextField label="Nome do pet" value={name} onChange={(e) => setName(e.target.value)} required />

          <SelectField label="Porte" value={size} onChange={(e) => setSize(e.target.value as SizeCategory)}>
            <option value="SMALL">Pequeno</option>
            <option value="MEDIUM">Médio</option>
            <option value="LARGE">Grande</option>
          </SelectField>

          <TextField
            label="Peso (kg)"
            type="number"
            step="0.1"
            value={weight}
            onChange={(e) => setWeight(e.target.value)}
          />

          <TextField
            label="Observações de saúde"
            value={healthIssues}
            onChange={(e) => setHealthIssues(e.target.value)}
            hint="Alergias, restrições ou cuidados especiais — opcional."
          />

          {error && (
            <p className="body" style={{ color: "var(--danger)", margin: 0 }}>
              {error}
            </p>
          )}

          <Button variant="primary" type="submit" disabled={saving}>
            {saving ? "Salvando…" : "Salvar pet"}
          </Button>
        </form>
      )}
    </div>
  );
}
