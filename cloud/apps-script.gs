/**
 * Photobooth → Google Drive uploader
 * 1. Buka https://script.google.com → Proyek baru → tempel seluruh kode ini.
 * 2. (Opsional) isi SECRET dengan kata sandi bebas, lalu isi kunci yang sama di aplikasi.
 * 3. Deploy → Deployment baru → Jenis: Aplikasi web
 *      Jalankan sebagai : Saya (akun Anda)
 *      Siapa yang memiliki akses : Siapa saja (Anyone)
 * 4. Izinkan akses Google Drive, salin URL Web App (berakhiran /exec) ke aplikasi.
 */
const SECRET = '';                 // contoh: 'pesta2026'
const DEFAULT_FOLDER = 'Photobooth';

function doPost(e) {
  try {
    const req = JSON.parse(e.postData.contents);
    if (SECRET && req.key !== SECRET) return json({ ok: false, error: 'Kunci rahasia salah' });

    const folder = getFolder(req.folder || DEFAULT_FOLDER);
    const bytes = Utilities.base64Decode(req.data);
    const blob = Utilities.newBlob(bytes, req.mimeType || 'image/jpeg', req.filename || ('foto_' + Date.now() + '.jpg'));
    const file = folder.createFile(blob);
    file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);

    return json({ ok: true, id: file.getId(), url: 'https://drive.google.com/file/d/' + file.getId() + '/view' });
  } catch (err) {
    return json({ ok: false, error: String(err) });
  }
}

function doGet() {
  return json({ ok: true, message: 'Uploader Photobooth aktif' });
}

function getFolder(name) {
  const root = DriveApp.getFoldersByName('Photobooth Uploads');
  const parent = root.hasNext() ? root.next() : DriveApp.createFolder('Photobooth Uploads');
  const it = parent.getFoldersByName(name);
  return it.hasNext() ? it.next() : parent.createFolder(name);
}

function json(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}
