import { useRef } from "react";

import type { Route } from "./+types/home";
import { ApiDocs } from "../components/ApiDocs";
import { Footer } from "../components/Footer";
import { Hero } from "../components/Hero";
import { HowItWorks } from "../components/HowItWorks";
import { JobStatusCard } from "../components/JobStatusCard";
import { MapPanel } from "../components/MapPanel";
import { Navbar } from "../components/Navbar";
import { OptimizerForm } from "../components/OptimizerForm";
import { ResultPanels } from "../components/ResultPanels";
import { SubmitBar } from "../components/SubmitBar";
import { TypeSelector } from "../components/TypeSelector";
import { useOptimizerState } from "../lib/hooks/useOptimizerState";
import { useRoutingJob } from "../lib/hooks/useRoutingJob";
import { useTheme } from "../lib/hooks/useTheme";
import { useToast } from "../lib/hooks/useToast";
import type { ProblemType } from "../lib/types";

export function meta({}: Route.MetaArgs) {
  return [
    { title: "opt · Otimização de rotas — TSP, VRP e matriz de distâncias" },
    {
      name: "description",
      content:
        "Resolva problemas de roteirização gratuitamente: caixeiro viajante (TSP), frota de veículos (VRP) e matriz de distâncias, direto no navegador.",
    },
  ];
}

export default function Home() {
  const { toast, showToast } = useToast();
  const { theme, toggleTheme } = useTheme();
  const statusRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<HTMLDivElement>(null);

  const optimizer = useOptimizerState({ showToast });

  const routing = useRoutingJob({
    problemType: optimizer.problemType,
    matrixType: optimizer.matrixType,
    origin: optimizer.origin,
    points: optimizer.points,
    vehicles: optimizer.vehicles,
    webhookUrl: optimizer.webhookUrl,
    showToast,
    statusRef,
  });

  /* Ao trocar o tipo de problema, reseta o estado do job em curso. */
  const changeProblemType = (type: ProblemType) => {
    optimizer.setProblemType(type);
    routing.reset();
  };

  return (
    <main className="opt-page-bg min-h-dvh">
      <Navbar theme={theme} onToggleTheme={toggleTheme} />
      <Hero />
      <HowItWorks />

      <section
        id="criar"
        className="mx-auto max-w-6xl scroll-mt-24 px-4 pb-16 sm:px-6"
      >
        <TypeSelector
          problemType={optimizer.problemType}
          onSelect={changeProblemType}
        />

        <OptimizerForm
          problemType={optimizer.problemType}
          matrixType={optimizer.matrixType}
          origin={optimizer.origin}
          points={optimizer.points}
          vehicles={optimizer.vehicles}
          geoBusy={optimizer.geoBusy}
          fileInputRef={optimizer.fileInputRef}
          onMatrixTypeChange={optimizer.setMatrixType}
          onOriginChange={(patch) =>
            optimizer.setOrigin((prev) => ({ ...prev, ...patch }))
          }
          onAddPoint={optimizer.addPoint}
          onUpdatePoint={optimizer.updatePoint}
          onRemovePoint={optimizer.removePoint}
          onAddVehicle={optimizer.addVehicle}
          onUpdateVehicle={optimizer.updateVehicle}
          onRemoveVehicle={optimizer.removeVehicle}
          onClearPoints={optimizer.clearPoints}
          onOriginGeocode={optimizer.handleOriginGeocode}
          onAddByAddress={optimizer.handleAddByAddress}
          onUseMyLocation={optimizer.useMyLocation}
          onFile={optimizer.handleFile}
          mapPanel={
            <MapPanel
              points={optimizer.mapPoints}
              routes={routing.mapRoutes}
              dark={theme === "dark"}
              problemType={optimizer.problemType}
              onPointDrag={optimizer.handlePointDrag}
              onMapClick={optimizer.handleMapClick}
              containerRef={mapRef}
              addPointMode={optimizer.addPointMode}
              onToggleAddPointMode={optimizer.toggleAddPointMode}
            />
          }
        />

        <SubmitBar
          problemType={optimizer.problemType}
          matrixType={optimizer.matrixType}
          points={optimizer.points}
          vehicles={optimizer.vehicles}
          origin={optimizer.origin}
          webhookUrl={optimizer.webhookUrl}
          submitting={routing.submitting}
          validationError={routing.validationError}
          onWebhookChange={optimizer.setWebhookUrl}
          onOptimize={() => void routing.handleOptimize()}
        />

        {routing.job && (
          <div ref={statusRef}>
            <JobStatusCard
              job={routing.job}
              status={routing.status}
              polls={routing.polls}
              curlForJob={routing.curlForJob}
              pollingPaused={routing.pollingPaused}
              onShowToast={showToast}
            />
          </div>
        )}

        {routing.output && routing.status?.status === "DONE" && (
          <ResultPanels
            problemType={optimizer.problemType}
            output={routing.output}
            matrix={routing.matrix}
            jobId={routing.job?.id}
            processingTimeMs={routing.status.processingTimeMs ?? undefined}
            onViewOnMap={() =>
              mapRef.current?.scrollIntoView({
                behavior: "smooth",
                block: "start",
              })
            }
          />
        )}
      </section>

      <ApiDocs />
      <Footer />

      {toast && (
        <div className="toast toast-center toast-bottom z-[80] px-4">
          <div
            role="alert"
            className={`alert shadow-xl ${
              toast.kind === "success"
                ? "alert-success"
                : toast.kind === "error"
                  ? "alert-error"
                  : "alert-info"
            }`}
          >
            <span>{toast.msg}</span>
          </div>
        </div>
      )}
    </main>
  );
}
