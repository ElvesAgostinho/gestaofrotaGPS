/**
 * A rede de segurança: um ecrã em branco nunca é aceitável.
 *
 * <p>Quando alguma coisa rebenta a meio de um ecrã, o React desmonta tudo e a
 * página fica branca. Do lado de quem usa, isso é indistinguível de «o sistema
 * avariou» — e de nada serve, porque não diz o que aconteceu nem o que fazer.
 *
 * <p>Aqui apanha-se o erro, mostra-se o que ele foi, e dá-se o botão que
 * resolve o caso mais comum: uma versão antiga da aplicação guardada no
 * telemóvel depois de uma actualização. Esse botão limpa a cópia local e
 * recarrega — que é o que um técnico faria, sem ter de ligar para ninguém.
 */
import { Component, type ErrorInfo, type ReactNode } from 'react';

interface Props {
  children: ReactNode;
}

interface State {
  erro: Error | null;
  onde: string | null;
}

/** Limpa o que está guardado no aparelho e recarrega de origem. */
export async function limparEActualizar() {
  try {
    if ('caches' in window) {
      const chaves = await caches.keys();
      await Promise.all(chaves.map((c) => caches.delete(c)));
    }
    if ('serviceWorker' in navigator) {
      const registos = await navigator.serviceWorker.getRegistrations();
      await Promise.all(registos.map((r) => r.unregister()));
    }
  } catch {
    /* mesmo sem limpar, recarregar costuma resolver */
  }
  window.location.replace(window.location.pathname + '?v=' + Date.now());
}

export class ErroNoEcra extends Component<Props, State> {
  state: State = { erro: null, onde: null };

  static getDerivedStateFromError(erro: Error): Partial<State> {
    return { erro };
  }

  componentDidCatch(erro: Error, info: ErrorInfo) {
    // Fica na consola para quem for ver, e no ecrã para quem não vai.
    console.error('Erro no ecrã:', erro, info.componentStack);
    this.setState({ onde: info.componentStack ?? null });
  }

  render() {
    const { erro, onde } = this.state;
    if (!erro) {
      return this.props.children;
    }
    return (
      <div
        style={{
          minHeight: '100dvh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: 24,
          background: '#141416',
          color: '#F4F4F5',
        }}
      >
        <div style={{ maxWidth: 560, width: '100%' }}>
          <div
            style={{
              background: '#141416',
              color: '#FFC62F',
              border: '1.5px solid #FFC62F',
              padding: '8px 12px',
              fontFamily: '"Barlow Condensed", Barlow, sans-serif',
              fontWeight: 700,
              fontSize: 16,
              letterSpacing: '0.04em',
              textTransform: 'uppercase',
            }}
          >
            Este ecrã não abriu
          </div>
          <div style={{ border: '1.5px solid #2A2C32', borderTop: 'none', padding: 16 }}>
            <p style={{ marginTop: 0, fontSize: 15, lineHeight: 1.5 }}>
              O resto do sistema continua a funcionar. Isto costuma acontecer quando o aparelho
              ainda tem uma versão antiga guardada depois de uma actualização.
            </p>
            <button
              type="button"
              onClick={() => void limparEActualizar()}
              style={{
                width: '100%',
                minHeight: 52,
                borderRadius: 10,
                border: 'none',
                background: '#FFC62F',
                color: '#141416',
                fontWeight: 700,
                fontSize: 16,
                cursor: 'pointer',
              }}
            >
              Actualizar e tentar de novo
            </button>
            <button
              type="button"
              onClick={() => window.history.back()}
              style={{
                width: '100%',
                minHeight: 44,
                marginTop: 10,
                borderRadius: 10,
                border: '1px solid #2A2C32',
                background: 'transparent',
                color: '#A1A1AA',
                fontSize: 14,
                cursor: 'pointer',
              }}
            >
              Voltar ao ecrã anterior
            </button>

            <details style={{ marginTop: 14, color: '#A1A1AA', fontSize: 12 }}>
              <summary style={{ cursor: 'pointer' }}>Detalhe técnico (para quem der apoio)</summary>
              <pre
                style={{
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                  marginTop: 8,
                  maxHeight: 220,
                  overflow: 'auto',
                }}
              >
                {erro.message}
                {onde ? `\n${onde}` : ''}
              </pre>
            </details>
          </div>
        </div>
      </div>
    );
  }
}
