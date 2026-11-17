import { useEffect, useState } from "react";
import { useAuth } from "../../context/AuthContext";
import * as customerService from "../../services/customerService";
import { ApiError } from "../../services/http";
import { PetAvatar } from "../../components/ui/PetAvatar";
import { Button } from "../../components/ui/Button";
import { TextField, SelectField } from "../../components/ui/Field";
import { EmptyState, Loading, Notice, PageHeader } from "../../components/ui/Feedback";
import { PlusIcon } from "../../components/ui/Icon";
import { AVATAR_OPTIONS } from "../../mocks/avatarOptions";
import type { Customer, PetType, SizeCategory } from "../../types/customer";

const SIZE_LABEL: Record<SizeCategory, string> = {
  SMALL: "Pequeno",
  MEDIUM: "Médio",
  LARGE: "Grande",
};

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

  if (!customer) {
    return (
      <div className="container page">
        <Loading>Chamando a turma…</Loading>
      </div>
    );
  }

  return (
    <div className="container page">
      <PageHeader eyebrow="a turma lá de casa" title="Meus pets">
        <Button variant={showForm ? "outline" : "primary"} onClick={() => setShowForm((v) => !v)}>
          {showForm ? (
            "Cancelar"
          ) : (
            <>
              <PlusIcon size={18} />
              Adicionar pet
            </>
          )}
        </Button>
      </PageHeader>

      <div className="stack" style={{ "--gap": "var(--space-8)" } as React.CSSProperties}>
        {showForm && (
          <form onSubmit={handleSubmit} className="card form" style={{ maxWidth: 720 }}>
            <h2 className="heading">Novo pet</h2>

            <fieldset className="fieldset">
              <legend className="field-label" style={{ fontFamily: "var(--font-sans)", fontSize: "0.9rem" }}>
                Com quem ele se parece?
              </legend>
              <div className="avatar-picker">
                {AVATAR_OPTIONS.map((option) => (
                  <label key={option.id} className="avatar-option">
                    <input
                      type="radio"
                      name="avatar"
                      value={option.id}
                      checked={avatarId === option.id}
                      onChange={() => setAvatarId(option.id)}
                    />
                    <img src={option.url} alt="" width={56} height={56} loading="lazy" />
                    {option.label}
                  </label>
                ))}
              </div>
            </fieldset>

            <TextField label="Nome do pet" value={name} onChange={(e) => setName(e.target.value)} required />

            <div className="field-row">
              <SelectField label="Porte" value={size} onChange={(e) => setSize(e.target.value as SizeCategory)}>
                <option value="SMALL">Pequeno</option>
                <option value="MEDIUM">Médio</option>
                <option value="LARGE">Grande</option>
              </SelectField>

              <TextField
                label="Peso (kg)"
                type="number"
                inputMode="decimal"
                step="0.1"
                min="0"
                value={weight}
                onChange={(e) => setWeight(e.target.value)}
              />
            </div>

            <TextField
              label="Observações de saúde"
              value={healthIssues}
              onChange={(e) => setHealthIssues(e.target.value)}
              hint="Alergias, restrições ou cuidados especiais — opcional."
            />

            {error && <Notice tone="danger">{error}</Notice>}

            <div>
              <Button variant="primary" type="submit" disabled={saving}>
                {saving ? "Salvando…" : "Salvar pet"}
              </Button>
            </div>
          </form>
        )}

        {customer.pets.length === 0 ? (
          !showForm && (
            <EmptyState title="Nenhum pet cadastrado ainda" note="quem vai ser o primeiro?">
              <Button variant="primary" onClick={() => setShowForm(true)}>
                Cadastrar meu pet
              </Button>
            </EmptyState>
          )
        ) : (
          <ul className="pet-grid">
            {customer.pets.map((pet) => {
              const avatar = AVATAR_OPTIONS.find((a) => a.label === pet.petBreed);
              return (
                <li key={pet.id} className="pet-card">
                  <PetAvatar name={pet.petName} petType={pet.petType} avatarUrl={avatar?.url} size={76} />
                  <div style={{ minWidth: 0 }}>
                    <p className="pet-card-name">{pet.petName}</p>
                    <dl>
                      <div>
                        <dt>Raça</dt>
                        <dd>{pet.petBreed}</dd>
                      </div>
                      <div>
                        <dt>Porte</dt>
                        <dd>{SIZE_LABEL[pet.petSize] ?? pet.petSize}</dd>
                      </div>
                      <div>
                        <dt>Peso</dt>
                        <dd>{pet.weightInKg} kg</dd>
                      </div>
                    </dl>
                    {pet.petHealthIssues && <p className="pet-card-note">obs.: {pet.petHealthIssues}</p>}
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </div>
  );
}
