# BlockUnknownCalls - Android Call Blocker

Um aplicativo Android moderno desenvolvido em Kotlin para o **Android 13 (API 33)**, focado em privacidade e produtividade. O app filtra chamadas recebidas, permitindo apenas números presentes na lista de contatos do usuário ou em uma Whitelist manual, bloqueando ou silenciando spams de forma automática.

## 🚀 Funcionalidades

- **Filtro Inteligente:** Identifica se o número está na agenda do dispositivo.
- **Whitelist Personalizada:** Permite adicionar exceções manualmente ou a partir de chamadas bloqueadas.
- **Histórico de Bloqueios:** Lista as chamadas interceptadas para consulta posterior.
- **Modos de Operação:**
    - **Block:** Rejeita a chamada imediatamente (sinal de ocupado).
    - **Mute:** Silencia a chamada e esconde a notificação, mas mantém o registro no sistema.
- **Notificações em Tempo Real:** Avisa o usuário sempre que uma ação de bloqueio for executada.

## 🛠 Tecnologias e APIs

- **Linguagem:** [Kotlin](https://kotlinlang.org/)
- **Android SDK:** API 33 (Android 13)
- **Componentes:**
    - `CallScreeningService`: API nativa para interceptação e filtragem de chamadas.
    - `RoleManager`: Gerenciamento de papéis (Roles) do sistema para definir o app como filtro padrão.
    - `SharedPreferences`: Persistência de dados para configurações e histórico.
    - `NotificationManager`: Sistema de notificações para Android 13+.

## 📋 Pré-requisitos

Para rodar o projeto, você precisará de:
- Android Studio Jellyfish ou superior.
- Dispositivo físico ou Emulador rodando **Android 13+**.
- Conexão USB para depuração habilitada.

## 🔧 Instalação e Configuração

1. **Clonar o repositório:**
   ```bash
   git clone [https://github.com/seu-usuario/block-unknown-calls.git](https://github.com/seu-usuario/block-unknown-calls.git)
