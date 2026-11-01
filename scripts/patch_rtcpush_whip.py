#!/usr/bin/env python3
"""
Patch LiveTalking rtcpush handler để support WHIP/SDP protocol với MediaMTX.

MediaMTX yêu cầu SDP phải có Content-Type: application/sdp header,
nhưng LiveTalking mặc định gửi text/plain; charset=utf-8 nên bị reject.

Script này áp dụng patch tự động vào vendored/LiveTalking source code.
Idempotent (safe to run multiple times).
"""

import os
import sys
from pathlib import Path


def find_live_talking_root():
    """Find vendored/LiveTalking directory from current working directory."""
    be_root = Path(__file__).parent.parent
    
    # Try relative to BE_ROOT first
    lt_path = be_root / "vendored" / "LiveTalking"
    if lt_path.exists() and (lt_path / "app.py").exists():
        return lt_path
    
    # Fallback to current directory
    lt_path = Path("vendored/LiveTalking")
    if lt_path.exists() and (lt_path / "app.py").exists():
        return lt_path
    
    print("[ERROR] Cannot find LiveTalking in vendored/LiveTalking", file=sys.stderr)
    return None


def get_original_content(lt_path: Path):
    """Get original rtc_manager.py content."""
    filepath = lt_path / "server" / "rtc_manager.py"
    
    if not filepath.exists():
        print(f"[ERROR] File not found: {filepath}", file=sys.stderr)
        return None
    
    with open(filepath, 'r', encoding='utf-8') as f:
        return f.read(), filepath


def apply_patch(content: str, filepath: Path):
    """Apply the rtcpush SDP content-type patch."""
    
    # Original line that needs fixing
    original_code = '''            if isinstance(data, dict):
                for key, value in data.items():
                    if key == "sdp":
                        answer_sdp = await response.text()'''
    
    # Patched version with proper Content-Type header
    patched_code = '''            if isinstance(data, dict):
                for key, value in data.items():
                    if key == "sdp":
                        # Ensure Content-Type is application/sdp for MediaMTX compatibility
                        response.headers["Content-Type"] = "application/sdp"
                        answer_sdp = await response.text()'''
    
    if original_code not in content:
        print("[SKIP] Patch already applied or different format detected", file=sys.stderr)
        print("[INFO] Checking alternative location...", file=sys.stderr)
        
        # Alternative: Check if it's in handle_offer method
        alt_original = '''async def handle_rtcpush(self, req):
        try:
            push_url, k = self.parse_push_url(req.url.path)
            if k not in self.rtcpush_sessions or self.rtcpush_sessions[k].status != "active":
                return web.Response(status=404)
            
            data = await req.post()
            sdp = data.get("sdp")
            if sdp:
                offer = web.RTCSessionDescription(sdp=sdp, type="offer")
                await self.rtcpush_sessions[k].handle_remote_description(offer)
            
            return web.Response(status=200)
        except Exception as e:
            self.logger.error(f"rtcpush error: {e}")
            return web.Response(status=500)'''
        
        alt_patched = '''async def handle_rtcpish(self, req):
        try:
            push_url, k = self.parse_push_url(req.url.path)
            if k not in self.rtcpush_sessions or self.rtcpush_sessions[k].status != "active":
                return web.Response(status=404)
            
            data = await req.post()
            sdp = data.get("sdp")
            if sdp:
                offer = web.RTCSessionDescription(sdp=sdp, type="offer")
                
                # Handle RTCPUSH SDP properly for WHIP protocol
                push_response = await self.rtsp_client.push(push_url, offer.sdp, 
                                                            content_type="application/sdp")
                if push_response.status != 201:
                    self.logger.error(f"Failed to push SDP: {push_response.status}")
                    return web.Response(status=500)
                
                answer_sdp = await push_response.text()
                
                answer = web.RTCSessionDescription(sdp=answer_sdp, type="answer")
                await self.rtcpush_sessions[k].handle_remote_description(answer)
            
            return web.Response(status=200)
        except Exception as e:
            self.logger.error(f"rtcpush error: {e}")
            return web.Response(status=500)'''
        
        if alt_original in content and alt_patched not in content:
            print("[INFO] Found alternative location, applying patch there...", file=sys.stderr)
            new_content = content.replace(alt_original, alt_patched)
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(new_content)
            print(f"[OK] Patched: {filepath} (alternative location)", file=sys.stderr)
            return True
        
        print(f"[ERROR] Could not find expected code in {filepath}", file=sys.stderr)
        print("[DEBUG] File content preview:", file=sys.stderr)
        lines = content.split('\n')
        for i, line in enumerate(lines[:50]):
            print(f"  Line {i}: {line}", file=sys.stderr)
        return False
    
    new_content = content.replace(original_code, patched_code)
    
    if new_content == content:
        print("[ERROR] No changes made - patch may already be applied", file=sys.stderr)
        return False
    
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(new_content)
    
    print(f"[OK] Patched: {filepath}", file=sys.stderr)
    print(f"  Added: response.headers['Content-Type'] = 'application/sdp'", file=sys.stderr)
    return True


def main():
    """Main entry point."""
    print("=" * 60)
    print("Applying rtcpush SDP Content-Type patch")
    print("=" * 60)
    
    lt_path = find_live_talking_root()
    if not lt_path:
        sys.exit(1)
    
    print(f"[INFO] LiveTalking root: {lt_path}", file=sys.stderr)
    
    # Get original content
    result = get_original_content(lt_path)
    if not result:
        sys.exit(1)
    
    content, filepath = result
    
    # Apply patch
    success = apply_patch(content, filepath)
    
    if success:
        print("\n" + "=" * 60)
        print("✅ Patch successfully applied!")
        print("=" * 60)
        print("\nNext steps:")
        print("  1. Run 'pip install -q -r requirements.txt' if needed")
        print("  2. Continue with step 3/8 (download weights)")
        print("")
        sys.exit(0)
    else:
        print("\n" + "=" * 60)
        print("❌ Patch failed - check above for errors")
        print("=" * 60)
        sys.exit(1)


if __name__ == "__main__":
    main()
