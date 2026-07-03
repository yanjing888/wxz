import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const root = resolve(import.meta.dirname, '..')

function read(path) {
  return readFileSync(resolve(root, path), 'utf8')
}

function assertContains(file, text, message) {
  const body = read(file)
  if (!body.includes(text)) {
    throw new Error(`${file}: ${message}`)
  }
}

function assertNotContains(file, text, message) {
  const body = read(file)
  if (body.includes(text)) {
    throw new Error(`${file}: ${message}`)
  }
}

assertNotContains('src/components/chat/Composer.vue', 'capture="environment"', 'composer camera entry should not use file capture upload')
assertNotContains('src/components/upload/ImageUploadZone.vue', 'capture="environment"', 'visual correction camera entry should not use file capture upload')
assertContains('src/components/chat/Composer.vue', "emit('capture-image')", 'composer should request the camera panel directly')
assertContains('src/components/upload/ImageUploadZone.vue', "emit('capture')", 'visual correction zone should request the camera panel directly')
assertContains('src/components/layout/RightPanel.vue', '@capture-image', 'right panel should forward composer capture events')
assertContains('src/views/LabView.vue', 'onComposerCapture', 'lab view should handle composer camera captures')
assertContains('src/views/LabView.vue', 'onZoneCapture', 'lab view should handle zone camera captures')
assertContains('src/views/LabView.vue', 'TabletCameraCapture', 'lab view should mount the shared tablet camera panel')
assertContains('src/views/LabView.vue', '@captured="onTabletCameraCaptured"', 'camera panel should return a captured frame to lab view')
assertNotContains('src/components/camera/TabletCameraCapture.vue', 'navigator.mediaDevices', 'production tablet photo entry should not depend on browser camera streams')
assertNotContains('src/components/camera/TabletCameraCapture.vue', 'getUserMedia', 'production tablet photo entry should not open browser camera streams')
assertNotContains('src/components/camera/TabletCameraCapture.vue', 'canvas.toBlob', 'production tablet photo entry should not capture video frames from a browser stream')
assertContains('src/components/camera/TabletCameraCapture.vue', 'type="file"', 'camera panel should use the mobile system camera input')
assertContains('src/components/camera/TabletCameraCapture.vue', 'accept="image/*"', 'system camera input should accept images from the tablet camera')
assertContains('src/components/camera/TabletCameraCapture.vue', 'capture="environment"', 'system camera input should prefer the rear tablet camera')
assertContains('src/components/camera/TabletCameraCapture.vue', '@change="onSystemPhotoChange"', 'system camera result should feed into the preview pipeline')
assertContains('src/components/camera/TabletCameraCapture.vue', 'openSystemCamera', 'camera panel should open the system camera as the primary path')
assertContains('src/components/camera/TabletCameraCapture.vue', 'previewUrl', 'system photo should preview inside the modal')
assertContains('src/components/camera/TabletCameraCapture.vue', 'pendingFile', 'system photo should wait for explicit confirmation before upload')
assertContains('src/components/camera/TabletCameraCapture.vue', 'URL.createObjectURL', 'system photo should render a local preview URL')
assertContains('src/components/camera/TabletCameraCapture.vue', 'confirmPhoto', 'system photo should have an explicit use-photo action')
assertContains('src/components/camera/TabletCameraCapture.vue', '使用照片', 'camera panel should expose a confirm button for the preview')
assertContains('src/components/camera/TabletCameraCapture.vue', '重拍', 'camera panel should expose a retake button in preview mode')

console.log('photo capture entry wiring present')
