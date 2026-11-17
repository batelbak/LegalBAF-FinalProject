# Running LegalBAF with a local model

Everything runs on your own PC through [Ollama](https://ollama.com). You don't need an API key and it costs nothing.
The default model is **Gemma 4 12B** (`gemma4:12b`, about 8 GB). It fits an 8 GB card like an RTX 4060, with a little spilling into system RAM.

## On the 4060 PC: the short version

1. Get this folder onto the PC, with your uncommitted changes (see *Getting the code there* below).
2. Double-click **`run-local.cmd`**, or run it from a terminal in this folder.
3. That's it. The script:
   - checks for **Java 17+** and **Ollama**, and if one is missing offers to install it with `winget` (type `yes`, then reopen the window and run it again)
   - starts Ollama and downloads `gemma4:12b` once (about 8 GB)
   - builds the project (the Maven wrapper downloads Maven itself, so nothing else is needed)
   - **judge pass:** the local judge weighs the real submissions of all 5 cases
   - **simulation:** the local agents argue all 5 cases for 3 rounds
   - opens the website at http://localhost:8080

Every step saves to `data\local\ollama-gemma4-12b\` and is skipped on the next run, so the second run opens the site almost immediately.

**How long it takes:** expect very roughly 10–30 minutes per case for the first full run (one judge pass plus 3 rounds × 3 calls). It depends on how much of the model spills from the GPU into RAM. Leave it running.

## Seeing the graphs without a model (demo data)

The website has an **engine** selector next to the hearing mode:
- **Model**: the real agents and judge. This needs Ollama running, or a Claude key.
- **Demo data (no model)**: placeholder arguments marked `[DEMO]`, with random links and random judge scores. Everything downstream is the real code: the attack/support tree, the DF-QuAD strengths, σ(C) and the verdict rule. Use it to see the visuals and the mechanics. It is **not an analysis**. Demo results are never saved.

When the model isn't running, the page selects Demo automatically and the header says "(not running)". To see only the website, run `run-local.cmd -SkipJudge -SkipSimulation`, or start the server any other way.

## Choosing the semantics

The verdict is computed with **DF-QuAD** by default. To use one of the other two semantics from the paper, set the environment variable `LEGALSIM_SEMANTICS` to `quad` or `euler` before starting. Every verdict also shows σ(C) under all three, so you can see whether a case depends on the choice. See `docs/QBAF-MODEL.md`.

## Why the judge pass exists

The stage-A scores in `data\assessments\` were made by Claude. Comparing a Gemma-judged simulation against a Claude-judged baseline would compare two judges, not real arguments against simulated ones. So the local model judges the real submissions again, and each model keeps its own folder:

| Folder | Contents |
|---|---|
| `data\cases`, `data\raw` | The cases, shared by all models |
| `data\assessments`, `data\graphs` | Stage A, judged by Claude (unchanged) |
| `data\local\<model>\assessments`, `graphs` | The real submissions, judged by the local model |
| `data\local\<model>\simulations` | Simulations: transcript, events, timeline, comparison, graph |

## Options

```
run-local.cmd -Cases 001-164453            # one case only (good for a first test)
run-local.cmd -Fresh                       # run the simulations again even if saved
run-local.cmd -SkipJudge -SkipSimulation   # just open the website
run-local.cmd -NoServer                    # compute everything, don't start the site
run-local.cmd -Think                       # thinking mode: maybe a better judge, much slower
run-local.cmd -Model gemma4:26b            # a different model (see below)
```

A good first test is `run-local.cmd -Cases 001-164453`, which runs one case end to end.

## Trying a bigger model

`gemma4:26b` is a mixture-of-experts model with about 26B parameters, but only about 4B are active per token. On the 32 GB PC it should run at a usable speed even though most of it sits in system RAM, and it should be noticeably smarter. Run `run-local.cmd -Model gemma4:26b`. Its results go to their own folder, so you can compare the two models.

Any model from https://ollama.com/library works the same way.

## If it's slow or runs out of memory

- Close games and browsers that use the GPU.
- Turn on Ollama's memory savers: set these as user environment variables in Windows, then restart Ollama from the tray icon:
  - `OLLAMA_FLASH_ATTENTION` = `1`
  - `OLLAMA_KV_CACHE_TYPE` = `q8_0`
- Check where the model is loaded with `ollama ps` while it runs. The PROCESSOR column shows the GPU/CPU split.

## Troubleshooting

| Message | What to do |
|---|---|
| `Ollama is not running` | Open the Ollama app from the Start menu, or run `ollama serve` |
| `Model ... is not installed` | `ollama pull gemma4:12b` |
| Ollama says the model needs a newer version | `winget upgrade Ollama.Ollama` |
| `Judge returned no score for ...` | The model skipped an argument 3 times in a row. Run again, or try `-Think` or a bigger model |
| The site says the judge has not assessed a case | Run `run-local.cmd` without `-SkipJudge` |

## Switching back to Claude

Set `LEGALSIM_LLM=claude` and `LEGALSIM_API_KEY=<your key>`. Results then go to the original `data\` folders. All settings live in `src/main/java/ac/qbaf/legalsim/llm/Llm.java`.

## Getting the code there

These changes are **not on GitHub**. They exist only as uncommitted edits in the laptop's clone. Either:
- commit them to a branch, push it, and clone or pull that branch on the 4060 PC, or
- copy this whole folder (skipping `target\`) with a USB stick or cloud drive.
