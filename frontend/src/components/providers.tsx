import { Provider as ReduxProvider } from 'react-redux'
import { PropsWithChildren } from 'react'
import { store, useSocketBridge } from '@/store'
import { Toaster } from '@/components/ui/toaster'

function SocketBridge() {
  useSocketBridge()
  return null
}

export function Providers({ children }: PropsWithChildren) {
  return (
    <ReduxProvider store={store}>
      <SocketBridge />
      {children}
      <Toaster />
    </ReduxProvider>
  )
}
