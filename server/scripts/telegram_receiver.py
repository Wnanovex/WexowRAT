import os
import requests
import time
import zipfile
import shutil

BOT_TOKEN = "YOUR_BOT_TOKEN"
CHAT_ID = "YOUR_CHAT_ID"

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DOWNLOAD_DIR = os.path.join(BASE_DIR, "received_zips")
EXTRACTED_DIR = os.path.join(BASE_DIR, "extracted")
MERGED_DIR = os.path.join(BASE_DIR, "merged")
FINAL_DIR = os.path.join(BASE_DIR, "final")

os.makedirs(DOWNLOAD_DIR, exist_ok=True)
os.makedirs(EXTRACTED_DIR, exist_ok=True)
os.makedirs(MERGED_DIR, exist_ok=True)
os.makedirs(FINAL_DIR, exist_ok=True)


def get_updates(offset=None):
    url = f"https://api.telegram.org/bot{BOT_TOKEN}/getUpdates"
    params = {"timeout": 30, "offset": offset}
    try:
        response = requests.get(url, params=params, timeout=35)
        return response.json()
    except Exception as e:
        print(f"❌ Connection error: {e}")
        return None


def download_file(file_id, file_name):
    file_info_url = f"https://api.telegram.org/bot{BOT_TOKEN}/getFile?file_id={file_id}"
    response = requests.get(file_info_url).json()
    
    if not response.get("ok"):
        print(f"❌ Failed: {response}")
        return None
    
    file_path = response["result"]["file_path"]
    download_url = f"https://api.telegram.org/file/bot{BOT_TOKEN}/{file_path}"
    
    print(f"📥 Downloading: {file_name}...")
    file_response = requests.get(download_url, stream=True)
    save_path = os.path.join(DOWNLOAD_DIR, file_name)
    
    with open(save_path, 'wb') as f:
        for chunk in file_response.iter_content(chunk_size=8192):
            f.write(chunk)
    
    size = os.path.getsize(save_path)
    print(f"✅ Downloaded: {file_name} ({size / 1024 / 1024:.1f} MB)")
    return save_path


def extract_zip(zip_path, folder_name):
    try:
        extract_path = os.path.join(EXTRACTED_DIR, folder_name)
        os.makedirs(extract_path, exist_ok=True)
        
        with zipfile.ZipFile(zip_path, 'r') as zip_ref:
            zip_ref.extractall(extract_path)
        
        print(f"📂 Extracted: {folder_name}")
        return extract_path
    except Exception as e:
        print(f"❌ Extract error: {e}")
        return None


def merge_chunks(base_name):
    try:
        chunks = [f for f in os.listdir(DOWNLOAD_DIR) 
                  if f.startswith(base_name + "_chunk") and f.endswith('.bin')]
        
        if not chunks:
            return None
        
        chunks.sort(key=lambda x: int(x.split('_chunk')[1].split('_of')[0]))
        
        merged_path = os.path.join(MERGED_DIR, base_name + ".zip")
        
        print(f"📦 Merging {len(chunks)} chunks into {merged_path}...")
        
        with open(merged_path, 'wb') as out:
            for chunk_name in chunks:
                chunk_path = os.path.join(DOWNLOAD_DIR, chunk_name)
                with open(chunk_path, 'rb') as inp:
                    shutil.copyfileobj(inp, out)
        
        size = os.path.getsize(merged_path) / 1024 / 1024
        print(f"✅ Merged: {merged_path} ({size:.1f} MB)")
        return merged_path
    except Exception as e:
        print(f"❌ Merge error: {e}")
        return None


def merge_parts(base_name):
    try:
        final_path = os.path.join(FINAL_DIR, base_name)
        os.makedirs(final_path, exist_ok=True)
        
        part_dirs = [d for d in os.listdir(EXTRACTED_DIR) 
                     if d.startswith(base_name) and os.path.isdir(os.path.join(EXTRACTED_DIR, d))]
        
        if not part_dirs:
            return None
        
        print(f"📦 Merging {len(part_dirs)} parts into {final_path}...")
        
        for part_dir in part_dirs:
            source = os.path.join(EXTRACTED_DIR, part_dir)
            for root, dirs, files in os.walk(source):
                for file in files:
                    src_file = os.path.join(root, file)
                    rel_path = os.path.relpath(src_file, source)
                    dst_file = os.path.join(final_path, rel_path)
                    os.makedirs(os.path.dirname(dst_file), exist_ok=True)
                    
                    if not os.path.exists(dst_file):
                        shutil.copy2(src_file, dst_file)
        
        print(f"✅ Final: {final_path}")
        return final_path
    except Exception as e:
        print(f"❌ Merge parts error: {e}")
        return None


def main():
    print("=" * 60)
    print("🚀 Telegram File Receiver")
    print("=" * 60)
    print(f"📁 ZIPs:      {DOWNLOAD_DIR}")
    print(f"📁 Extracted: {EXTRACTED_DIR}")
    print(f"📁 Merged:    {MERGED_DIR}")
    print(f"📁 Final:     {FINAL_DIR}")
    print(f"📊 Waiting for files...\n")
    
    last_update_id = None
    received_zips = []
    received_bins = {}  # {base_name: [chunks]}
    received_zips_by_base = {}
    
    while True:
        updates = get_updates(last_update_id)
        
        if updates and updates.get("ok"):
            for update in updates["result"]:
                last_update_id = update["update_id"] + 1
                
                if "message" in update and "document" in update["message"]:
                    msg = update["message"]
                    doc = msg["document"]
                    
                    file_name = doc.get("file_name", "file.bin")
                    file_size = doc.get("file_size", 0)
                    caption = msg.get("caption", "")
                    
                    print(f"\n{'=' * 60}")
                    print(f"📥 {file_name} ({file_size / 1024 / 1024:.1f} MB)")
                    if caption:
                        print(f"📝 {caption}")
                    print(f"{'=' * 60}")
                    
                    saved_path = download_file(doc["file_id"], file_name)
                    
                    if not saved_path:
                        continue
                    
                    # ⭐ base_name
                    base_name = caption.split('_part')[0] if '_part' in caption else os.path.splitext(file_name)[0]
                    
                    # ⭐ ZIP 
                    if file_name.endswith('.zip'):
                        if base_name not in received_zips_by_base:
                            received_zips_by_base[base_name] = []
                        received_zips_by_base[base_name].append(saved_path)
                        
                        folder_name = os.path.splitext(file_name)[0]
                        extract_zip(saved_path, folder_name)
                    
                    elif file_name.endswith('.bin'):
                        if base_name not in received_bins:
                            received_bins[base_name] = []
                        received_bins[base_name].append(file_name)
                        print(f"📊 {base_name}: {len(received_bins[base_name])} chunks received")
        
        time.sleep(2)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\n👋 Stopped")
