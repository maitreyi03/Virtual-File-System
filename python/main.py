# NAME : MAITREYI PAREEK
# UCINETID: MAITREYP
# STUDENT ID: 76592576
# CS143B: PROJECT 1

class FileSystem:
    def __init__(self):
        # Constants
        self.BLOCK_COUNT = 64
        self.BLOCK_SIZE = 512
        self.MAX_FILE_SIZE = 3  # Maximum blocks per file
        self.MAX_FILENAME_LENGTH = 3
        self.DESCRIPTOR_SIZE = 4
        self.MAX_DESCRIPTORS = 192
        self.ENTRY_SIZE = 8  # 4 chars for name + 4 bytes for descriptor index
        self.DIR_BLOCK = 7  # Directory block number
        
        # Disk and memory
        self.disk = [[0] * self.BLOCK_SIZE for _ in range(self.BLOCK_COUNT)]
        self.memory = [0] * self.BLOCK_SIZE  # Memory buffer M
        
        # Cache for bitmap and descriptors
        self.bitmap = [0] * self.BLOCK_COUNT
        self.descriptors = [[0] * self.DESCRIPTOR_SIZE for _ in range(self.MAX_DESCRIPTORS)]
        
        # Open File Table (OFT)
        self.oft = [
            {
                'buffer': [0] * self.BLOCK_SIZE,
                'position': -1,
                'size': 0,
                'descriptor_index': -1
            } 
            for _ in range(4)
        ]
        
        self.c = 0
        self.initialize_system()

    def initialize_system(self):
        """Initialize the file system to starting configuration"""
        #self.flag = True
        self.c += 1
        # Reset caches
        self.bitmap = [0] * self.BLOCK_COUNT
        self.descriptors = [[-1, 0, 0, 0] for _ in range(self.MAX_DESCRIPTORS)]
        
        # Mark system blocks as used (0-7)
        for i in range(8):
            self.bitmap[i] = 1
            
        # Initialize directory descriptor
        self.descriptors[0] = [0, self.DIR_BLOCK, 0, 0]
        
        # Save bitmap and descriptors
        self._save_bitmap()
        self._save_descriptors()
        
        # Reset OFT
        for i in range(len(self.oft)):
            self.oft[i] = {
                'buffer': [0] * self.BLOCK_SIZE,
                'position': -1,
                'size': 0,
                'descriptor_index': -1
            }
        
        # Initialize directory in OFT[0]
        self.oft[0] = {
            'buffer': [0] * self.BLOCK_SIZE,
            'position': 0,
            'size': 0,
            'descriptor_index': 0
        }
        return "system initialized" if self.c == 2 else "\nsystem initialized \n"

    def create(self, name):
        """Create a new file"""
        if not self._validate_filename(name):
            return "error"
            
        if self._find_file(name) != -1:
            return "error"
            
        desc_idx = self._find_free_descriptor()
        if desc_idx == -1:
            return "error"
            
        # Initialize descriptor
        self.descriptors[desc_idx] = [0, 0, 0, 0]
        
        # Add directory entry
        if not self._add_directory_entry(name, desc_idx):
            self.descriptors[desc_idx] = [-1, 0, 0, 0]  # Rollback
            return "error"
            
        self._save_descriptors()
        self._write_directory_block()
        
        return f"{name} created"

    def destroy(self, name):
        """Destroy a file"""
        desc_idx = self._find_file(name)
        if desc_idx == -1:
            return "error"
            
        # Check if file is open
        for oft_entry in self.oft:
            if oft_entry['descriptor_index'] == desc_idx:
                return "error"
                
        # Free blocks
        for block in self.descriptors[desc_idx][1:]:
            if block != 0:
                self.bitmap[block] = 0
                
        # Mark descriptor as free
        self.descriptors[desc_idx] = [-1, 0, 0, 0]
        
        # Remove directory entry
        self._remove_directory_entry(name)
        
        self._save_bitmap()
        self._save_descriptors()
        self._write_directory_block()
        
        return f"{name} destroyed"

    def open(self, name):
        """Open a file"""
        desc_idx = self._find_file(name)
        if desc_idx == -1:
            return "error"
            
        # Check if file is already open
        for i, oft_entry in enumerate(self.oft):
            if oft_entry['descriptor_index'] == desc_idx:
                return "error"
                
        # Find free OFT entry
        oft_idx = self._find_free_oft()
        if oft_idx == -1:
            return "error"
            
        # Initialize OFT entry
        self.oft[oft_idx] = {
            'buffer': [0] * self.BLOCK_SIZE,
            'position': 0,
            'size': self.descriptors[desc_idx][0],
            'descriptor_index': desc_idx
        }
        
        # Load first block if exists
        first_block = self.descriptors[desc_idx][1]
        if first_block != 0:
            self._read_block(first_block, self.oft[oft_idx]['buffer'])
            
        return f"{name} opened {oft_idx}"

    def close(self, index):
        """Close a file"""
        try:
            index = int(index)
            if not self._is_valid_oft(index):
                return "error"
                
            oft_entry = self.oft[index]
            desc_idx = oft_entry['descriptor_index']
            
            # Write buffer if needed
            curr_block = self._get_current_block_number(oft_entry)
            if curr_block != 0:
                self._write_block(curr_block, oft_entry['buffer'])
            
            # Update descriptor
            self.descriptors[desc_idx][0] = oft_entry['size']
            self._save_descriptors()
            
            # Clear OFT entry
            self.oft[index] = {
                'buffer': [0] * self.BLOCK_SIZE,
                'position': -1,
                'size': 0,
                'descriptor_index': -1
            }
            
            return f"{index} closed"
        except:
            return "error"

    def read(self, index, mem_pos, count):
        """Read from file to memory"""
        try:
            index = int(index)
            mem_pos = int(mem_pos)
            count = int(count)
            
            if not self._is_valid_oft(index) or not self._is_valid_memory(mem_pos, count):
                return "error"
                
            oft_entry = self.oft[index]
            bytes_read = 0
            
            while bytes_read < count and oft_entry['position'] < oft_entry['size']:
                # Calculate block and offset
                block_idx = oft_entry['position'] // self.BLOCK_SIZE
                offset = oft_entry['position'] % self.BLOCK_SIZE
                
                # Load new block if needed
                if offset == 0:
                    block = self.descriptors[oft_entry['descriptor_index']][1 + block_idx]
                    if block != 0:
                        self._read_block(block, oft_entry['buffer'])
                
                # Read byte
                self.memory[mem_pos + bytes_read] = oft_entry['buffer'][offset]
                bytes_read += 1
                oft_entry['position'] += 1
                
            return f"{bytes_read} bytes read from file {index}"
        except:
            return "error"

    def write(self, index, mem_pos, count):
        """Write from memory to file"""
        try:
            index = int(index)
            mem_pos = int(mem_pos)
            count = int(count)
            
            if not self._is_valid_oft(index) or not self._is_valid_memory(mem_pos, count):
                return "error"
                
            oft_entry = self.oft[index]
            desc_idx = oft_entry['descriptor_index']
            bytes_written = 0
            
            while bytes_written < count:
                block_idx = oft_entry['position'] // self.BLOCK_SIZE
                if block_idx >= self.MAX_FILE_SIZE:
                    break
                    
                offset = oft_entry['position'] % self.BLOCK_SIZE
                
                # Write current block if full
                if offset == 0 and bytes_written > 0:
                    prev_block = self.descriptors[desc_idx][block_idx]
                    if prev_block != 0:
                        self._write_block(prev_block, oft_entry['buffer'])
                        
                # Allocate new block if needed
                if offset == 0:
                    if self.descriptors[desc_idx][1 + block_idx] == 0:
                        new_block = self._find_free_block()
                        if new_block == -1:
                            break
                        self.bitmap[new_block] = 1
                        self.descriptors[desc_idx][1 + block_idx] = new_block
                        self._save_bitmap()
                        oft_entry['buffer'] = [0] * self.BLOCK_SIZE
                
                # Write byte
                oft_entry['buffer'][offset] = self.memory[mem_pos + bytes_written]
                bytes_written += 1
                oft_entry['position'] += 1
                
                # Update file size
                if oft_entry['position'] > oft_entry['size']:
                    oft_entry['size'] = oft_entry['position']
            
            # Write final buffer
            if bytes_written > 0:
                block_idx = (oft_entry['position'] - 1) // self.BLOCK_SIZE
                block = self.descriptors[desc_idx][1 + block_idx]
                if block != 0:
                    self._write_block(block, oft_entry['buffer'])
            
            # Update descriptor
            self.descriptors[desc_idx][0] = oft_entry['size']
            self._save_descriptors()
            
            return f"{bytes_written} bytes written to file {index}"
        except:
            return "error"

    def seek(self, index, pos):
        """Set current position of file"""
        try:
            index = int(index)
            pos = int(pos)
            
            if not self._is_valid_oft(index):
                return "error"
                
            oft_entry = self.oft[index]
            if pos < 0 or pos > oft_entry['size']:
                return "error"
                
            # Write current block if changing blocks
            old_block = oft_entry['position'] // self.BLOCK_SIZE
            new_block = pos // self.BLOCK_SIZE
            
            if old_block != new_block:
                curr_block = self._get_current_block_number(oft_entry)
                if curr_block != 0:
                    self._write_block(curr_block, oft_entry['buffer'])
                    
                # Load new block
                block = self.descriptors[oft_entry['descriptor_index']][1 + new_block]
                if block != 0:
                    self._read_block(block, oft_entry['buffer'])
                    
            oft_entry['position'] = pos
            return f"position is {pos}"
        except:
            return "error"

    def directory(self):
        """List all files and their sizes"""
        files = []
        for i in range(self.oft[0]['size'] // self.ENTRY_SIZE):
            name, desc_idx = self._read_directory_entry(i)
            if desc_idx != -1:
                size = self.descriptors[desc_idx][0]
                files.append(f"{name} {size}")
        return " ".join(files)

    def write_memory(self, pos, data):
        """Write string to memory"""
        try:
            pos = int(pos)
            if not self._is_valid_memory(pos, len(data)):
                return "error"
                
            for i, char in enumerate(data):
                self.memory[pos + i] = ord(char)
            return f"{len(data)} bytes written to M"
        except:
            return "error"

    def read_memory(self, pos, count):
        """Read from memory"""
        try:
            pos = int(pos)
            count = int(count)
            
            if not self._is_valid_memory(pos, count):
                return "error"
                
            result = ""
            for i in range(count):
                if self.memory[pos + i] != 0:
                    result += chr(self.memory[pos + i])
            return result
        except:
            return "error"

    def _read_block(self, block_num, buffer):
        """Read block from disk into buffer"""
        buffer[:] = self.disk[block_num][:]

    def _write_block(self, block_num, buffer):
        """Write buffer to disk block"""
        self.disk[block_num][:] = buffer[:]

    def _save_bitmap(self):
        """Save bitmap cache to disk"""
        block = [0] * self.BLOCK_SIZE
        for i, bit in enumerate(self.bitmap):
            if bit:
                block[i // 8] |= (1 << (i % 8))
        self._write_block(0, block)

    def _save_descriptors(self):
        """Save descriptor cache to disk"""
        for block in range(6):
            buffer = [0] * self.BLOCK_SIZE
            for i in range(32):
                desc_idx = block * 32 + i
                if desc_idx < self.MAX_DESCRIPTORS:
                    base = i * self.DESCRIPTOR_SIZE
                    buffer[base:base + self.DESCRIPTOR_SIZE] = self.descriptors[desc_idx]
            self._write_block(block + 1, buffer)

    def _find_free_block(self):
        """Find first free block"""
        for i in range(8, self.BLOCK_COUNT):
            if not self.bitmap[i]:
                return i
        return -1

    def _find_free_descriptor(self):
        """Find first free descriptor"""
        for i in range(1, self.MAX_DESCRIPTORS):
            if self.descriptors[i][0] == -1:
                return i
        return -1

    def _find_free_oft(self):
        """Find first free OFT entry"""
        for i in range(1, len(self.oft)):
            if self.oft[i]['position'] == -1:
                return i
        return -1

    def _find_file(self, name):
        """Find descriptor index for file"""
        for i in range(self.oft[0]['size'] // self.ENTRY_SIZE):
            curr_name, desc_idx = self._read_directory_entry(i)
            if curr_name == name:
                return desc_idx
        return -1

    def _read_directory_entry(self, index):
        """Read directory entry at index"""
        base = index * self.ENTRY_SIZE
        name = ""
        for i in range(4):
            char = self.oft[0]['buffer'][base + i]
            if char != 0:
                name += chr(char)
        desc_idx = self.oft[0]['buffer'][base + 4]
        return name.strip(), desc_idx

    def _add_directory_entry(self, name, desc_idx):
        """Add new directory entry"""
        if self.oft[0]['size'] + self.ENTRY_SIZE > self.BLOCK_SIZE:
            return False
            
        pos = self.oft[0]['size']
        name = name.ljust(4, '\0')
        
        # Write name
        for i, char in enumerate(name):
            self.oft[0]['buffer'][pos + i] = ord(char)
            
        # Write descriptor index
        self.oft[0]['buffer'][pos + 4] = desc_idx
        
        # Update directory size
        self.oft[0]['size'] += self.ENTRY_SIZE
        return True

    def _remove_directory_entry(self, name):
        """Remove directory entry"""
        entry_count = self.oft[0]['size'] // self.ENTRY_SIZE
        for i in range(entry_count):
            curr_name, desc_idx = self._read_directory_entry(i)
            if curr_name == name:
                # Shift remaining entries
                start = i * self.ENTRY_SIZE
                end = start + self.ENTRY_SIZE
                for j in range(end, self.oft[0]['size']):
                    self.oft[0]['buffer'][j - self.ENTRY_SIZE] = self.oft[0]['buffer'][j]
                    
                # Update size
                self.oft[0]['size'] -= self.ENTRY_SIZE
                return True
        return False

    def _is_valid_oft(self, index):
        """Check if OFT index is valid and entry is in use"""
        return (isinstance(index, int) and 
                0 <= index < len(self.oft) and 
                self.oft[index]['position'] != -1)

    def _is_valid_memory(self, pos, count):
        """Check if memory access is within bounds"""
        return (isinstance(pos, int) and 
                isinstance(count, int) and 
                pos >= 0 and 
                count >= 0 and 
                pos + count <= self.BLOCK_SIZE)

    def _validate_filename(self, name):
        """Validate filename format and length"""
        if not isinstance(name, str):
            return False
        if len(name) == 0 or len(name) > self.MAX_FILENAME_LENGTH:
            return False
        # Only allow alphanumeric characters
        return all(c.isalnum() for c in name)

    def _get_current_block_number(self, oft_entry):
        """Get current block number based on position"""
        block_idx = oft_entry['position'] // self.BLOCK_SIZE
        if block_idx >= self.MAX_FILE_SIZE:
            return 0
        return self.descriptors[oft_entry['descriptor_index']][1 + block_idx]

    def _write_directory_block(self):
        """Write directory buffer to disk"""
        self._write_block(self.DIR_BLOCK, self.oft[0]['buffer'])

    def _compact_directory(self):
        """Remove gaps in directory entries"""
        valid_entries = []
        entry_count = self.oft[0]['size'] // self.ENTRY_SIZE
        
        # Collect valid entries
        for i in range(entry_count):
            name, desc_idx = self._read_directory_entry(i)
            if desc_idx != -1:
                valid_entries.append((name, desc_idx))
                
        # Clear directory buffer
        self.oft[0]['buffer'] = [0] * self.BLOCK_SIZE
        self.oft[0]['size'] = 0
        
        # Rewrite valid entries
        for name, desc_idx in valid_entries:
            self._add_directory_entry(name, desc_idx)

def main():
    """Main function to process commands"""
    file_name = input("Enter the file name")
    #file_name = "FS-input-1.txt"
    fs = FileSystem()
    
    try:
        with open(file_name, "r") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                    
                parts = line.split()
                cmd = parts[0]
                result = "error"
                
                try:
                    if cmd == "cr" and len(parts) == 2:
                        result = fs.create(parts[1])
                    elif cmd == "de" and len(parts) == 2:
                        result = fs.destroy(parts[1])
                    elif cmd == "op" and len(parts) == 2:
                        result = fs.open(parts[1])
                    elif cmd == "cl" and len(parts) == 2:
                        result = fs.close(parts[1])
                    elif cmd == "rd" and len(parts) == 4:
                        result = fs.read(parts[1], parts[2], parts[3])
                    elif cmd == "wr" and len(parts) == 4:
                        result = fs.write(parts[1], parts[2], parts[3])
                    elif cmd == "sk" and len(parts) == 3:
                        result = fs.seek(parts[1], parts[2])
                    elif cmd == "dr" and len(parts) == 1:
                        result = fs.directory()
                    elif cmd == "in" and len(parts) == 1:
                        result = fs.initialize_system()
                    elif cmd == "rm" and len(parts) == 3:
                        result = fs.read_memory(parts[1], parts[2])
                    elif cmd == "wm" and len(parts) >= 3:
                        data = " ".join(parts[2:])
                        result = fs.write_memory(parts[1], data)
                except:
                    result = "error"
                    
                print(result) 
                with open("output.txt", "a") as out_f:
                    out_f.write(result + "\n")
                    
    except FileNotFoundError:
        print("Input file not found")
    except Exception as e:
        print(f"Error processing commands: {str(e)}")

if __name__ == "__main__":
    main()