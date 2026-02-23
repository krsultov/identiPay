import Navigation from "@/components/Navigation";
import Hero from "@/components/Hero";
import ProblemSection from "@/components/ProblemSection";
import ProtocolOverview from "@/components/ProtocolOverview";
import SettlementAbstraction from "@/components/SettlementAbstraction";
import FederatedRouting from "@/components/FederatedRouting";
import HowItWorks from "@/components/HowItWorks";
import PaymentFlows from "@/components/PaymentFlows";
import TechnicalDeep from "@/components/TechnicalDeep";
import UseCases from "@/components/UseCases";
import IntegrationSection from "@/components/IntegrationSection";
import WhitepaperSection from "@/components/WhitepaperSection";
import SecuritySection from "@/components/SecuritySection";
import Footer from "@/components/Footer";

export default function Home() {
  return (
    <>
      <Navigation />
      <main>
        <Hero />
        <ProblemSection />
        <ProtocolOverview />
        <SettlementAbstraction />
        <FederatedRouting />
        <HowItWorks />
        <PaymentFlows />
        <TechnicalDeep />
        <UseCases />
        <IntegrationSection />
        <SecuritySection />
        <WhitepaperSection />
      </main>
      <Footer />
    </>
  );
}

