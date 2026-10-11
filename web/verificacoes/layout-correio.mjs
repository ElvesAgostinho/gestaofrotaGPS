// Prova que o conteúdo do correio fica dentro da moldura.
//
// O defeito era este: a altura do corpo era adivinhada em pixéis e as colunas
// não tinham `min-height: 0`, portanto não encolhiam abaixo do seu conteúdo —
// e a lista de mensagens escorria para fora do painel, por cima da página.
//
// O que se mede é o CONTENTOR, não o seu último filho: dentro de uma área que
// rola, o último filho está legitimamente abaixo da vista. A primeira versão
// deste teste media o filho e dava falha com o layout correcto.
import { chromium } from 'playwright-core';
import { pathToFileURL } from 'node:url';
import path from 'node:path';

const ficheiro = pathToFileURL(path.resolve(process.argv[2])).href;
const browser = await chromium.launch({ executablePath: process.env.CHROME_BIN });

let falhas = 0;
for (const [largura, altura] of [
  [1600, 900],
  [1280, 720],
  [1024, 700],
]) {
  const page = await browser.newPage({ viewport: { width: largura, height: altura } });
  await page.goto(ficheiro);
  await page.waitForTimeout(150);

  const r = await page.evaluate(() => {
    const painel = document.getElementById('painel').getBoundingClientRect();
    const medir = (id) => {
      const el = document.getElementById(id);
      const caixa = el.getBoundingClientRect();
      return {
        // Quanto é que a área de leitura passa para fora do painel.
        foraPorBaixo: Math.round(caixa.bottom - painel.bottom),
        altura: Math.round(caixa.height),
        // Se rola, o conteúdo está contido e não empurrou a moldura.
        rola: el.scrollHeight > el.clientHeight + 1,
      };
    };
    return {
      painelFundo: Math.round(painel.bottom),
      painelAltura: Math.round(painel.height),
      janela: window.innerHeight,
      // O documento não pode crescer: se cresce, alguma coisa transbordou.
      documento: Math.round(document.documentElement.scrollHeight),
      lista: medir('lista'),
      leitor: medir('leitor'),
    };
  });

  const dentroDaJanela = r.painelFundo <= r.janela;
  const semCrescerAPagina = r.documento <= r.janela + 1;
  const listaContida = r.lista.foraPorBaixo <= 0 && r.lista.rola;
  const leitorContido = r.leitor.foraPorBaixo <= 0 && r.leitor.rola;
  const ok = dentroDaJanela && semCrescerAPagina && listaContida && leitorContido;
  if (!ok) {
    falhas += 1;
  }
  console.log(
    `${largura}x${altura}  painel ${r.painelAltura}px acaba a ${r.painelFundo} ` +
      `(janela ${r.janela}, documento ${r.documento})  ` +
      `lista ${r.lista.altura}px fora=${r.lista.foraPorBaixo} rola=${r.lista.rola}  ` +
      `leitor ${r.leitor.altura}px fora=${r.leitor.foraPorBaixo} rola=${r.leitor.rola}  ` +
      `${ok ? 'OK' : 'FALHA'}`,
  );
  await page.close();
}

await browser.close();
process.exit(falhas === 0 ? 0 : 1);
