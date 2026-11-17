import { Link } from "react-router-dom";
import { Brand } from "../ui/Logo";
import { useAuth } from "../../context/AuthContext";

export function Footer() {
  const { isAuthenticated } = useAuth();

  return (
    <footer className="footer">
      <div className="container footer-grid">
        <div>
          <Brand light />
          <p className="footer-note">Produtos e serviços para quem ama pets — e para quem é amado por eles.</p>
        </div>

        <div>
          <h2>A loja</h2>
          <address>
            Rua das Patinhas, 123
            <br />
            Compras com retirada no balcão
          </address>
        </div>

        <div>
          <h2>Por aqui</h2>
          <ul>
            <li>
              <Link to="/">Loja</Link>
            </li>
            <li>
              <Link to="/agendar">Banho, tosa e veterinário</Link>
            </li>
            <li>
              <Link to="/carrinho">Carrinho</Link>
            </li>
          </ul>
        </div>

        <div>
          <h2>Sua conta</h2>
          <ul>
            {isAuthenticated ? (
              <>
                <li>
                  <Link to="/minha-conta/pedidos">Meus pedidos</Link>
                </li>
                <li>
                  <Link to="/minha-conta/agendamentos">Agendamentos</Link>
                </li>
                <li>
                  <Link to="/minha-conta/pets">Meus pets</Link>
                </li>
              </>
            ) : (
              <>
                <li>
                  <Link to="/entrar">Entrar</Link>
                </li>
                <li>
                  <Link to="/cadastro">Criar conta</Link>
                </li>
              </>
            )}
            <li>
              <Link to="/interno">Área da equipe</Link>
            </li>
          </ul>
        </div>
      </div>

      <div className="container footer-bottom">
        <span>WebCommerce PetShop — projeto de estudo, sem deploy público.</span>
        <span>Feito com carinho (e alguns pelos no teclado).</span>
      </div>
    </footer>
  );
}
